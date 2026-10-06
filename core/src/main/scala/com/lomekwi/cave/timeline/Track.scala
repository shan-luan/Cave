package com.lomekwi.cave.timeline

import com.lomekwi.cave.collection.BiMap
import com.lomekwi.cave.pipeline.{BlockSource, Boundless, Clip, Content, Element, EvalClock, Frame, Gap, Segment, Transition}

import java.io.Serializable
import java.util
import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.immutable.TreeMap
import scala.jdk.CollectionConverters.*

/**
 * 轨道。不可变，每次编辑返回新实例。
 *
 * 只存片段，空隙不存储，[[get]] 与 [[rangeAt]] 落进空隙时临时物化一个 [[Gap]] 返回。
 * 转场由相邻内容的重叠区派生，由轨道自行维护，不通过 setter 直接修改。
 */
@SerialVersionUID(1L)
final class Track private (val timeline: Timeline, val index: Int,
                           private val blockSegment: Segment,
                           private val layout: Track.Layout,
                           private val segToOrigin: Map[Segment, Long],
                           private val transitions: Track.TransitionIndex) extends Serializable with java.lang.Iterable[Segment] {

  private def isBlock(segment: Segment): Boolean = segment.eq(blockSegment)

  private def intervalToSeg: TreeMap[Interval, Segment] = layout.forward

  /** 是否没有用户内容。 */
  protected[timeline] def isEmpty: Boolean = intervalToSeg.valuesIterator.forall(isBlock)

  /** 片段占用的区间。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def getRange(segment: Segment): Interval = {
    require(layout.containsValue(segment))
    layout.reverse(segment)
  }

  /** 片段的 0 秒在时间轴中的位置。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def getOrigin(segment: Segment): Long = {
    require(segToOrigin.contains(segment))
    segToOrigin(segment)
  }

  def contains(segment: Segment): Boolean = layout.containsValue(segment)

  /** 最后一个片段的终点；没有片段时为 0。 */
  lazy val length: Long = intervalToSeg.iterator.filter { case (_, s) => !isBlock(s) }.map(_._1.hi).maxOption.getOrElse(0L)

  /** 按起点顺序的下一个内容片段；没有时返回 null。 */
  private[timeline] def nextContent(c: Content): Content = contentAtOrAfter(getRange(c).lo + 1, null)

  /** 按起点顺序的上一个内容片段；没有时返回 null。 */
  private[timeline] def prevContent(c: Content): Content = contentBefore(getRange(c).lo, null)

  /** left 与 right 之间的转场，仅在两者确实相邻时返回；否则 null。 */
  private[timeline] def transitionBetween(left: Content, right: Content): Transition = {
    transitions.get(left) match {
      case Some(t) if nextContent(left) eq right => t
      case _ => null
    }
  }

  /** c 右侧紧邻的转场；没有时返回 null。 */
  def transitionAfter(c: Content): Transition = placedTransitionAfter(c)

  /** c 左侧紧邻的转场；没有时返回 null。 */
  def transitionBefore(c: Content): Transition = {
    val p = prevContent(c)
    if (p == null) null else placedTransitionAfter(p)
  }

  private def placedTransitionAfter(c: Content): Transition = {
    val t = transitions.get(c).orNull
    if (t != null && contains(t)) t else null
  }

  /** 条目独占播放的终点。有右侧转场时内容只播到转场起点，转场则播满自己的区间。 */
  def soloEndOf(segment: Segment): Long = segment match {
    case c: Content =>
      val r = getRange(c)
      val t = placedTransitionAfter(c)
      if (t == null) r.hi else Math.min(r.hi, getRange(t).lo)
    case _ => getRange(segment).hi
  }

  /** 转场两侧的内容，左前右后；转场不在本轨道时返回 null。 */
  def transitionSides(t: Transition): (Content, Content) = {
    val left = transitions.getKey(t).orNull
    if (left == null || !contains(left)) null else (left, nextContent(left))
  }

  /**
   * 尝试把内容片段放进指定区间，与已有内容的重叠自然成为转场。
   * @return 放得下时是 `(新轨道, 0)`；放不下时不加入，返回 `(本轨道, 最近的可容纳偏移)`
   */
  protected[timeline] def tryAdd(segment: Segment, r: Interval, origin: Long): (Track, Long) = segment match {
    case _: Transition =>
      throw new IllegalArgumentException("不允许手动添加转场片段")
    case c: Content =>
      if (canPlaceAt(c, r)) (put(c, r, origin).syncAround(c), 0L) else (this, getShift(r))
  }

  /** 把内容片段放到指定区间。放不下时抛 IllegalArgumentException。要求片段不在本轨道上。 */
  protected[timeline] def addOrThrow(segment: Segment, r: Interval, origin: Long): Track = segment match {
    case _: Transition =>
      throw new IllegalArgumentException("不允许手动添加转场片段")
    case c: Content =>
      if (!canPlaceAt(c, r)) throw new IllegalArgumentException("目标区间无法放置内容: " + r)
      put(c, r, origin).syncAround(c)
  }

  private def put(element: Segment, r: Interval, origin: Long): Track =
    derived(layout.updated(r, element), segToOrigin.updated(element, origin))

  private def drop(element: Segment): Track =
    derived(layout.removedValue(element), segToOrigin.removed(element))

  private def canPlaceAt(c: Content, r: Interval): Boolean = canPlaceAt(c, r, null)

  private[timeline] def canPlaceAt(c: Content, r: Interval, skip: util.Collection[Segment]): Boolean = {
    if (r.isEmpty) return false
    val left = contentBefore(r.lo, skip)
    if (left != null) {
      val lr = getRange(left)
      // 与左邻居重叠时必须交叉，否则新条目会被左邻居包含
      if (lr.hi > r.lo && !(lr.lo < r.lo && lr.hi < r.hi && canTransition(left, c))) return false
      val ll = contentBefore(getRange(left).lo, skip)
      if (ll != null && getRange(ll).hi > r.lo) return false
    }
    val right = contentAtOrAfter(r.lo, skip)
    if (right != null) {
      val rr = getRange(right)
      // 与右邻居重叠时必须交叉
      if (r.hi > rr.lo && !(r.lo < rr.lo && r.hi < rr.hi && canTransition(c, right))) return false
      val rn = contentAtOrAfter(getRange(right).lo + 1, skip)
      if (rn != null && r.hi > getRange(rn).lo) return false
    }
    // 新条目把原来的相邻对拆成了隔项，左右邻居之间从此不能再重叠
    if (left != null && right != null && getRange(left).hi > getRange(right).lo) return false
    true
  }

  /**
   * 起点小于 time 的最后一个内容片段，跳过 skip 里的条目；没有时返回 null。
   */
  private def contentBefore(time: Long, skip: util.Collection[Segment]): Content = {
    var bound = at(time)
    var entry = intervalToSeg.rangeUntil(bound).lastOption
    while (entry.isDefined) {
      val current = entry.get
      current._2 match {
        case x: Content if !isBlock(x) && (skip == null || !skip.contains(x)) => return x
        case _ =>
      }
      bound = current._1
      entry = intervalToSeg.rangeUntil(bound).lastOption
    }
    null
  }

  /** 起点不小于 time 的第一个内容片段，跳过 skip 里的条目；没有时返回 null。 */
  private def contentAtOrAfter(time: Long, skip: util.Collection[Segment]): Content = {
    intervalToSeg.rangeFrom(at(time)).iterator
      .map(_._2)
      .collectFirst { case x: Content if !isBlock(x) && (skip == null || !skip.contains(x)) => x }
      .orNull
  }

  private def syncAround(c: Content): Track = {
    val p = prevContent(c)
    val t = if (p == null) this else syncTransition(p)
    t.syncTransition(c)
  }

  private def syncTransition(left: Content): Track = {
    val old = transitions.get(left).orNull
    val right = nextContent(left)
    if (right == null) {
      // 右邻居暂时缺席，索引项留着等它回来，表里的条目先摘掉
      unplaceTransition(left)
    } else {
      val lo = getRange(right).lo
      val hi = getRange(left).hi
      if (lo < hi) {
        if (old != null && contains(old)) {
          val current = getRange(old)
          // 已在对应区间时无需改动
          if (current.lo == lo && current.hi == hi) this
          else put(old, lo ~~ hi, lo)
        } else {
          val tr = if (old == null) newTransition(left, right) else old
          put(tr, lo ~~ hi, lo).indexTransition(left, tr)
        }
      } else if (old == null) {
        this
      } else {
        // 两侧内容都在却不再重叠，表里的条目与索引项都要摘掉
        val cleared = if (contains(old)) drop(old) else this
        cleared.unindexTransition(left)
      }
    }
  }

  private def unplaceTransition(c: Content): Track = {
    val old = transitions.get(c).orNull
    if (old != null && contains(old)) drop(old) else this
  }

  private def indexTransition(left: Content, tr: Transition): Track =
    withTransitions(transitions.updated(left, tr))

  private def unindexTransition(left: Content): Track =
    withTransitions(transitions.removed(left))

  /** 移除片段。片段不在本轨道时原样返回本实例。 */
  protected[timeline] def remove(segment: Segment): Track = {
    if (!contains(segment)) {
      this
    } else {
      segment match {
        case t: Transition => removeTransition(t)
        case c: Content => removeContent(c)
      }
    }
  }

  private def removeContent(c: Content): Track = {
    val p = prevContent(c)
    val n = nextContent(c)
    var t = drop(c).unplaceTransition(c)
    if (p != null && n != null) {
      val pr = t.getRange(p)
      val nr = t.getRange(n)
      if (nr.lo < pr.hi && !(pr.lo < nr.lo && pr.hi < nr.hi)) {
        val target = Math.max(nr.lo, pr.hi)
        if (target < nr.hi) {
          t = t.put(n, target ~~ nr.hi, t.getOrigin(n))
        }
      }
    }
    if (p == null) t else t.syncTransition(p)
  }

  /**
   * 删除转场，即消除两侧内容的重叠。无法在不破坏布局的前提下消除时放弃本次删除，原样返回本实例。
   */
  private def removeTransition(t: Transition): Track = {
    val sides = transitionSides(t)
    if (sides == null) return this
    val (left, right) = sides
    val tr = getRange(t)
    val lr = getRange(left)
    val rr = getRange(right)
    val half = (tr.hi - tr.lo) / 2
    val lower = Math.max(Math.max(lr.lo + 1, minStartOf(right)), endLower(left))
    var boundary = lr.hi - half
    boundary = Math.max(boundary, lower)
    boundary = Math.min(boundary, rr.hi - 1)
    val rn = nextContent(right)
    if (rn != null) {
      boundary = Math.min(boundary, getRange(rn).lo - 1)
    }
    // 右后继的上限把交界点压回下界之下时两侧内容不再交叉，消除重叠无解，放弃本次删除
    if (boundary < lower || boundary <= lr.lo || boundary >= rr.hi) return this
    val next = drop(t)
      .put(left, lr.lo ~~ boundary, getOrigin(left))
      .put(right, boundary ~~ rr.hi, getOrigin(right))
    next.syncAround(left)
  }

  /** 移除这批片段，返回新版本。不在本轨道的片段会被忽略。 */
  protected[timeline] def removeAll(segments: util.Collection[Segment]): Track = {
    var t = this
    for (s <- segments.asScala) {
      t = t.remove(s)
    }
    t
  }

  /** 在 time 处把内容片段一分为二。time 不落在内容片段的区间内部时原样返回本实例。 */
  protected[timeline] def split(time: Long): Track = {
    val s = segmentAt(time)
    if (s == null) return this
    s match {
      case c: Content =>
        val r = layout.reverse(c)
        if (time <= r.lo || time >= r.hi) return this
        val origin: Long = getOrigin(c)
        val right = c.duplicate().asInstanceOf[Content]
        // 两半共用 origin，片段内时间是绝对时间减 origin，右半才接得上左半的内容
        val halves = drop(c)
          .put(c, r.lo ~~ time, origin)
          .put(right, time ~~ r.hi, origin)
        // 右半是新内容，它右侧的转场要重新建
        halves.syncAround(c).syncTransition(right)
      case _ => this
    }
  }

  /** 裁切一组片段的起始边缘（各自终点不变）。 */
  protected[timeline] def setStart(segments: util.Collection[Segment], deltaTime: Long): Track = {
    var t = this
    for (s <- segments.asScala) {
      if (t.contains(s)) t = t.setStart(s, deltaTime)
    }
    t
  }

  protected[timeline] def setStart(segment: Segment, deltaTime: Long): Track = segment match {
    case t: Transition => setTransitionStart(t, deltaTime)
    case c: Content => setContentStart(c, deltaTime)
  }

  /** 裁切一组片段的结束边缘（各自起点不变）。 */
  protected[timeline] def setEnd(segments: util.Collection[Segment], deltaTime: Long): Track = {
    var t = this
    for (s <- segments.asScala) {
      if (t.contains(s)) t = t.setEnd(s, deltaTime)
    }
    t
  }

  protected[timeline] def setEnd(segment: Segment, deltaTime: Long): Track = segment match {
    case t: Transition => setTransitionEnd(t, deltaTime)
    case c: Content => setContentEnd(c, deltaTime)
  }

  private def setContentStart(c: Content, deltaTime: Long): Track = {
    val r = getRange(c)
    val lower = startLower(c)
    val upper = startUpper(c)
    if (lower > upper) return this
    val target = Math.max(lower, Math.min(upper, r.lo + deltaTime))
    if (target == r.lo) return this
    put(c, target ~~ r.hi, getOrigin(c)).syncAround(c)
  }

  private def setContentEnd(c: Content, deltaTime: Long): Track = {
    val r = getRange(c)
    val lower = endLower(c)
    val upper = endUpper(c)
    if (lower > upper) return this
    val target = Math.max(lower, Math.min(upper, r.hi + deltaTime))
    if (target == r.hi) return this
    put(c, r.lo ~~ target, getOrigin(c)).syncAround(c)
  }

  private def setTransitionStart(t: Transition, deltaTime: Long): Track = {
    val sides = transitionSides(t)
    if (sides == null) return this
    setContentStart(sides._2, deltaTime)
  }

  private def setTransitionEnd(t: Transition, deltaTime: Long): Track = {
    val sides = transitionSides(t)
    if (sides == null) return this
    setContentEnd(sides._1, deltaTime)
  }

  private def startLower(c: Content): Long = {
    val r = getRange(c)
    var lower = Math.max(0L, minStartOf(c))
    val p = prevContent(c)
    if (p != null) {
      val pr = getRange(p)
      val cap = if (canTransition(p, c) && r.hi > pr.hi) pr.lo + 1 else pr.hi
      lower = Math.max(lower, cap)
      val pp = prevContent(p)
      if (pp != null) {
        lower = Math.max(lower, getRange(pp).hi)
      }
    }
    lower
  }

  private def startUpper(c: Content): Long = {
    val r = getRange(c)
    var upper = r.hi - 1
    val n = nextContent(c)
    if (n != null) {
      val nr = getRange(n)
      if (nr.lo > r.lo && r.hi > nr.lo) {
        upper = Math.min(upper, nr.lo - 1)
      }
    }
    upper
  }

  private def endLower(c: Content): Long = {
    val r = getRange(c)
    var lower = r.lo + 1
    val p = prevContent(c)
    if (p != null) {
      val pr = getRange(p)
      if (pr.hi > r.lo) {
        lower = Math.max(lower, pr.hi + 1)
      }
    }
    lower
  }

  private def endUpper(c: Content): Long = {
    val r = getRange(c)
    var upper = maxEndOf(c)
    val n = nextContent(c)
    if (n != null) {
      val nr = getRange(n)
      val cap = if (canTransition(c, n)) nr.hi - 1 else nr.lo
      upper = Math.min(upper, cap)
      val nn = nextContent(n)
      if (nn != null) {
        upper = Math.min(upper, getRange(nn).lo)
      }
    }
    upper
  }

  private def canTransition(from: Content, to: Content): Boolean = {
    from.canCreateTransitionWith(to) || to.canCreateTransitionWith(from)
  }

  private def newTransition(from: Content, to: Content): Transition = {
    // 后段兜底构造出的转场源，[[TransitionSource.from]]与[[TransitionSource.to]]相对时间顺序互换
    if (from.canCreateTransitionWith(to)) from.createTransition(to) else to.createTransition(from)
  }

  /** 整条转场刚性平移，转场宽度不变。 */
  protected[timeline] def shiftTransition(t: Transition, delta: Long): Track = {
    if (delta == 0 || !contains(t)) return this
    val sides = transitionSides(t)
    if (sides == null) return this
    val (left, right) = sides
    val lr = getRange(left)
    val rr = getRange(right)
    // 两侧同步移动，只需保证移动后仍交叉且不越过各自的素材与邻居约束
    val minShift = Math.max(
      Math.max(Math.max(minStartOf(right) - rr.lo, lr.lo + 1 - rr.lo), endLower(left) - lr.hi),
      lr.lo + 1 - lr.hi)
    var maxShift = Math.min(maxEndOf(left) - lr.hi, rr.hi - lr.hi - 1)
    val rn = nextContent(right)
    if (rn != null) {
      maxShift = Math.min(maxShift, getRange(rn).lo - 1 - rr.lo)
    }
    if (minShift > maxShift) return this
    val applied = Math.max(minShift, Math.min(maxShift, delta))
    if (applied == 0) return this
    put(left, lr.lo ~~ (lr.hi + applied), getOrigin(left))
      .put(right, (rr.lo + applied) ~~ rr.hi, getOrigin(right))
      .syncAround(left)
  }

  private def derived(layout: Track.Layout,
                      segToOrigin: Map[Segment, Long]): Track =
    new Track(timeline, index, blockSegment, layout, segToOrigin, transitions)

  private def withTransitions(transitions: Track.TransitionIndex): Track =
    new Track(timeline, index, blockSegment, layout, segToOrigin, transitions)

  private def getShift(r: Interval): Long = pickShift(shiftScan(r, true), shiftScan(r, false))

  private def pickShift(forward: Long, backward: Long): Long = {
    val fOk = forward != Long.MaxValue
    val bOk = backward != Long.MinValue
    if (fOk && bOk) {
      if (Math.abs(forward) <= Math.abs(backward)) forward else backward
    } else if (fOk) {
      forward
    } else if (bOk) {
      backward
    } else {
      0
    }
  }

  private def shiftScan(r: Interval, forward: Boolean): Long = {
    val lo: Long = r.lo
    val hi: Long = r.hi

    @tailrec
    def scan(s: Long, step: Int): Long = {
      if (step >= Track.MAX_SLIDE_STEPS || noSegment(r.shift(s))) {
        s
      } else {
        val obstacles = intersectingEntries(intervalToSeg, r.shift(s))
          .map(_._1)
        val candidate =
          if (forward) obstacles.map(_.hi).maxOption.map(_ - lo)
          else obstacles.map(_.lo).minOption.map(_ - hi)
        candidate match {
          case None => s
          case Some(next) if forward && next <= s => Long.MaxValue
          case Some(next) if !forward && next >= s => s
          case Some(next) => scan(next, step + 1)
        }
      }
    }

    scan(0L, 0)
  }

  private def noSegment(range: Interval): Boolean =
    !intersectingEntries(intervalToSeg, range).hasNext

  /** range 内是否没有被 ignore 之外的片段占用。 */
  def isFree(range: Interval, ignore: util.Collection[Segment]): Boolean = {
    intersectingEntries(intervalToSeg, range).forall { case (_, s) => ignore.contains(s) }
  }

  protected[timeline] def probeSetStart(segments: util.Collection[Segment], forward: Boolean): Long = {
    val offsets = segments.asScala.iterator
      .filter((s: Segment) => contains(s))
      .map {
        case t: Transition => probeTransitionStart(t, forward)
        case c: Content => probeContentStart(c, forward)
      }
    // 各成员偏移与请求同号，正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(Long.MaxValue) else offsets.maxOption.getOrElse(Long.MinValue)
  }

  protected[timeline] def probeSetEnd(segments: util.Collection[Segment], forward: Boolean): Long = {
    val offsets = segments.asScala.iterator
      .filter((s: Segment) => contains(s))
      .map {
        case t: Transition => probeTransitionEnd(t, forward)
        case c: Content => probeContentEnd(c, forward)
      }
    // 各成员偏移与请求同号，正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(Long.MaxValue) else offsets.maxOption.getOrElse(Long.MinValue)
  }

  private def probeContentStart(c: Content, forward: Boolean): Long = {
    val r = getRange(c)
    if (forward) Math.max(startUpper(c) - r.lo, 0) else Math.min(startLower(c) - r.lo, 0)
  }

  private def probeContentEnd(c: Content, forward: Boolean): Long = {
    val r = getRange(c)
    if (forward) Math.max(endUpper(c) - r.hi, 0) else Math.min(endLower(c) - r.hi, 0)
  }

  private def probeTransitionStart(t: Transition, forward: Boolean): Long = {
    val sides = transitionSides(t)
    if (sides == null) 0L else probeContentStart(sides._2, forward)
  }

  private def probeTransitionEnd(t: Transition, forward: Boolean): Long = {
    val sides = transitionSides(t)
    if (sides == null) 0L else probeContentEnd(sides._1, forward)
  }

  /** 素材起点。有界素材是 origin，无界内容没有素材起点，视作时间轴 0。 */
  private def minStartOf(segment: Segment): Long = segment match {
    case _: Clip => Math.max(0, getOrigin(segment))
    case _ => 0L
  }

  /** 素材终点。有界素材是 origin 加时长，无界内容与转场没有素材终点。 */
  private def maxEndOf(segment: Segment): Long = segment match {
    case c: Clip => getOrigin(c) + c.getDuration
    case _ => Long.MaxValue
  }

  protected[timeline] def probeMove(segments: util.Collection[Segment], forward: Boolean): Long = {
    val noBlock: Long = if (forward) Long.MaxValue else Long.MinValue // 该方向无障碍，视作无界
    val moving = segments.asScala.toSet
    // 两侧内容都不动的转场要带着它们一起平移，得在摘掉整组后的布局上单独探测，按需构造
    lazy val bare: Track = removeAll(segments)
    val offsets = moving.iterator
      .filter((s: Segment) => contains(s))
      .map {
        case t: Transition =>
          val sides = transitionSides(t)
          if (sides == null || moving.contains(sides._1) || moving.contains(sides._2)) {
            // 至少一侧内容也在搬运范围内时，转场随内容重建，不构成独立限制
            noBlock
          } else {
            val (left, right) = sides
            bare.probeShiftOf(left, right, getRange(left), getRange(right),
              maxEndOf(left), minStartOf(right), forward)
          }
        case c: Content => probeMoveOf(c, getRange(c), moving, forward)
      }
    // 各成员偏移与请求同号，正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(noBlock) else offsets.maxOption.getOrElse(noBlock)
  }

  private def probeMoveOf(c: Content, r: Interval, moving: Set[Segment], forward: Boolean): Long = {
    if (forward) {
      var bound = Long.MaxValue
      val n = nextContent(c)
      if (n != null) {
        val nr = getRange(n)
        if (!moving.contains(n)) {
          bound = Math.min(bound,
            if (canTransition(c, n)) Math.min(nr.lo - 1 - r.lo, nr.hi - 1 - r.hi) else nr.lo - r.hi)
        }
        val nn = nextContent(n)
        if (nn != null && !moving.contains(nn)) {
          bound = Math.min(bound, getRange(nn).lo - r.hi)
        }
      }
      Math.max(bound, 0)
    } else {
      var bound = -r.lo
      val p = prevContent(c)
      if (p != null) {
        val pr = getRange(p)
        if (!moving.contains(p)) {
          val reach =
            if (canTransition(p, c)) Math.min(pr.hi - r.lo, Math.max(pr.lo + 1 - r.lo, pr.hi + 1 - r.hi))
            else pr.hi - r.lo
          bound = Math.max(bound, reach)
        }
        val pp = prevContent(p)
        if (pp != null && !moving.contains(pp)) {
          bound = Math.max(bound, getRange(pp).hi - r.lo)
        }
      }
      Math.min(bound, 0)
    }
  }

  private def probeShiftOf(left: Content, right: Content, lr: Interval, rr: Interval,
                           hiCap: Long, loCap: Long, forward: Boolean): Long = {
    def skip(x: Content): Boolean = x.eq(left) || x.eq(right)
    if (forward) {
      var bound = hiCap - lr.hi
      val n = intervalToSeg.rangeFrom(at(rr.lo)).iterator.map(_._2)
        .collectFirst { case x: Content if !isBlock(x) && !skip(x) => x }
        .orNull
      if (n != null) {
        bound = Math.min(bound, getRange(n).lo - rr.hi)
      }
      Math.max(bound, 0)
    } else {
      var bound = Math.max(loCap - rr.lo, lr.lo + 1 - lr.hi)
      val p = intervalToSeg.rangeUntil(at(lr.lo)).iterator.map(_._2)
        .collect { case x: Content if !isBlock(x) && !skip(x) => x }
        .toSeq.lastOption
        .orNull
      if (p != null) {
        bound = Math.max(bound, getRange(p).hi - lr.lo)
      }
      Math.min(bound, 0)
    }
  }

  /** 包含 time 的条目；落在空隙中时返回临时物化的 [[Gap]]。 */
  def get(time: Long): Element = {
    val s = segmentAt(time)
    if (s == null) new Gap else s
  }

  /** time 所在条目（片段、转场或空隙）的区间。重叠时取区间最短的条目。 */
  def rangeAt(time: Long): Interval = {
    val s = segmentAt(time)
    if (s != null) {
      getRange(s)
    } else {
      // 空隙从上一个条目的终点延伸到下一个条目的起点
      // 条目按 (lo, hi) 有序时 hi 不减（见 [[segmentAt]]），lo ≤ time 的条目里最后一个 hi 最大
      val from = lastAtOrBefore(upTo(time)) match {
        case Some((r, _)) => Math.max(0L, r.hi)
        case None => 0L
      }
      from ~~ nextStart(time)
    }
  }

  /** time 是否落在某个内容片段的区间内部，即能否在此分割。 */
  def canSplit(time: Long): Boolean = segmentAt(time) match {
    case c: Content =>
      val r = layout.reverse(c)
      time > r.lo && time < r.hi
    case _ => false
  }

  /** 起点顺序上紧随其后的条目；没有时返回 null。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def nextOf(segment: Segment): Segment = sourceAtOrAfter(getRange(segment).hi)

  /** 起点顺序上紧邻其前的条目；没有时返回 null。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def prevOf(segment: Segment): Segment = sourceBefore(getRange(segment).lo)

  /** 起点不小于 time 的首个片段；没有时返回 null。 */
  private[timeline] def sourceAtOrAfter(time: Long): Segment = {
    intervalToSeg.rangeFrom(at(time)).iterator.map(_._2).nextOption().orNull
  }

  /** 起点小于 time 的最后一个片段；没有时返回 null。 */
  private[timeline] def sourceBefore(time: Long): Segment = {
    lastBefore(intervalToSeg, time) match {
      case null => null
      case (_, s) => s
    }
  }

  /** 与 range 有公共点的用户片段，按区间升序；返回快照。 */
  def getIntersecting(range: Interval): util.List[Segment] = {
    intersectingEntries(intervalToSeg, range).map(_._2).filterNot(isBlock).toList.asJava
  }

  /** 生成片段在绝对时间 time 的帧，求值期间经 [[EvalClock]] 向节点提供时刻。 */
  def frameAt(segment: Segment, time: Long): Frame = {
    val relative = time - getOrigin(segment)
    val f = EvalClock.withTime(time, relative) {
      segment.get(relative, this)
    }
    if (f == null) null else f.withTime(time)
  }

  /** 把片段同步到绝对时间 time。 */
  def syncAt(segment: Segment, time: Long): Unit = {
    segment.sync(time - getOrigin(segment), this)
  }

  /** 本轨道的轨道线程。 */
  def getWorker: timeline.TrackWorker = timeline.getWorker(index)

  /** 轨道上的用户片段；阻挡片段对遍历不可见。 */
  override def iterator(): util.Iterator[Segment] = {
    intervalToSeg.valuesIterator.filterNot(isBlock).asJava
  }

  /** 调试用摘要，按时间顺序列出片段，内容附带 origin 与素材终点。 */
  override def toString: String = {
    val parts = intervalToSeg.iterator.filterNot((_, s) => isBlock(s)).map { case (r, s) =>
      s match {
        case c: Content =>
          c.displayName + "=[" + r.lo + "," + Track.timeText(r.hi) + ") o=" + segToOrigin(c) + " m=" + Track.timeText(maxEndOf(c))
        case t: Transition =>
          t.displayName + "=[" + r.lo + "," + Track.timeText(r.hi) + ")"
        case _ =>
          "Seg=[" + r.lo + "," + Track.timeText(r.hi) + ")"
      }
    }
    parts.mkString(", ")
  }

  /** 索引相同且条目逐项相同。条目都按起点升序迭代，两侧可以逐项对齐比较。 */
  override def equals(o: Any): Boolean = {
    if (this.asInstanceOf[AnyRef] eq o.asInstanceOf[AnyRef]) {
      true
    } else o match {
      case other: Track =>
        if (index != other.index) {
          false
        } else {
          val a = intervalToSeg.iterator.toIndexedSeq
          val b = other.intervalToSeg.iterator.toIndexedSeq
          a.size == b.size && a.zip(b).forall { case ((ia, ea), (ib, eb)) =>
            ia == ib && entryEquals(ea, eb, other)
          }
        }
      case _ => false
    }
  }

  /**
   * 比较两个条目，比较类型、时长与 origin；区间作为键已经比过。
   */
  private def entryEquals(a: Segment, b: Segment, other: Track): Boolean = {
    Track.sourceEquals(a, b) && getOrigin(a) == other.getOrigin(b)
  }

  override def hashCode(): Int = {
    Integer.hashCode(index)
  }

  /** 包含 time 的片段，落在空隙中时返回 null。重叠时取区间最短的那个，它一定是转场。 */
  private def segmentAt(time: Long): Segment = {
    // 布局不变量（放置约束见 [[canPlaceAt]]，平移与裁切的上下界计算与之配套）使条目按 (lo, hi)
    // 排序后 hi 不减，覆盖 time 的条目因而是 lo ≤ time 的条目末尾的连续几个，反向扫描
    // 遇到 hi ≤ time 的条目即可停止，其之前的条目 hi 更短，不可能覆盖 time
    var best: Segment = null
    var bestLen = Long.MaxValue
    var current = lastAtOrBefore(upTo(time))
    while (current.isDefined) {
      val (r, s) = current.get
      if (r.hi <= time) return best
      if (r.hi - r.lo < bestLen) {
        best = s
        bestLen = r.hi - r.lo
      }
      current = intervalToSeg.maxBefore(r)
    }
    best
  }

  /** 与 range 有公共点的条目，按区间升序。 */
  private def intersectingEntries(bt: immutable.TreeMap[Interval, Segment], range: Interval): Iterator[(Interval, Segment)] = {
    if (range.isEmpty) {
      Iterator.empty
    } else {
      bt.rangeUntil(at(range.hi)).iterator
        .dropWhile(_._1.hi <= range.lo)
    }
  }

  private def upTo(time: Long): Interval = time ~~ Long.MaxValue

  private def at(time: Long): Interval = time ~~ time

  private def lastBefore(bt: immutable.TreeMap[Interval, Segment], time: Long): (Interval, Segment) = {
    bt.maxBefore(at(time)).orNull
  }

  /** 键不大于 bound 的最后一个条目。 */
  private def lastAtOrBefore(bound: Interval): Option[(Interval, Segment)] =
    intervalToSeg.get(bound) match {
      case Some(s) => Some((bound, s))
      case None => intervalToSeg.maxBefore(bound)
    }

  private def nextStart(time: Long): Long = {
    intervalToSeg.minAfter(at(time)).map(_._1.lo).getOrElse(Long.MaxValue)
  }
}

object Track {
  private type Layout = BiMap[Interval, Segment, TreeMap[Interval, Segment], Map[Segment, Interval]]

  private type TransitionIndex = BiMap[Content, Transition, Map[Content, Transition], Map[Transition, Content]]

  /** 新建空轨道。 */
  private[timeline] def apply(timeline: Timeline, index: Int): Track = {
    val blockSegment: Segment = new Boundless(new BlockSource)
    val layout = BiMap(TreeMap.empty[Interval, Segment], Map.empty[Segment, Interval])
      .updated(Long.MinValue ~~ 0L, blockSegment)
    new Track(timeline, index, blockSegment, layout,
      Map[Segment, Long](blockSegment -> 0L),
      BiMap(Map.empty[Content, Transition], Map.empty[Transition, Content]))
  }

  private final val MAX_SLIDE_STEPS = 10000

  private def timeText(time: Long): String = {
    if (time == Long.MaxValue) "∞" else time.toString
  }

  private def sourceEquals(a: Segment, b: Segment): Boolean = {
    if (a.eq(b)) {
      true
    } else {
      a.getClass == b.getClass && a.getDuration == b.getDuration
    }
  }
}
