package com.lomekwi.cave.timeline

import com.lomekwi.cave.collection.BiMap
import com.lomekwi.cave.pipeline.{BlockSource, EvalClock, Frame}

import java.io.Serializable
import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.immutable.TreeMap

/**
 * 轨道。不可变，每次编辑返回新实例。
 * 转场由相邻内容的重叠区派生，由轨道自行维护，不直接修改。
 */
@SerialVersionUID(1L)
final class Track private (val index: Int,
                           private val blockSegment: Segment,
                           private val layout: Track.Layout,
                           private val segToOrigin: Map[Clip, Long],
                           private val transitions: Track.TransitionIndex) extends Serializable with Iterable[Segment] {

  private def isBlock(segment: Segment): Boolean = segment.eq(blockSegment)

  private def intervalToSeg: TreeMap[Interval, Segment] = layout.forward

  /** 是否没有用户内容。 */
  override def isEmpty: Boolean = intervalToSeg.valuesIterator.forall(isBlock)

  /** 条目占用的区间。片段要求在本轨道，否则抛 IllegalArgumentException；空隙由两侧邻居推出。 */
  def getInterval(element: Element): Interval = element match {
    case s: Segment =>
      require(layout.containsValue(s))
      layout.reverse(s)
    case Gap(before, after) =>
      val lo = before.map(getInterval(_).hi).getOrElse(0L)
      val hi = after.map(getInterval(_).lo).getOrElse(Long.MaxValue)
      lo ~~ hi
  }

  /** 片段的 0 秒在时间轴中的位置。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def getOrigin(segment: Segment): Long = segment match {
    case c: Clip =>
      require(segToOrigin.contains(c))
      segToOrigin(c)
    case _ =>
      getInterval(segment).lo
  }

  def contains(segment: Segment): Boolean = layout.containsValue(segment)

  def length: Long = intervalToSeg.last._1.hi

  /** 按起点顺序的下一个内容片段。 */
  private[timeline] def nextContent(c: Content): Option[Content] = contentAtOrAfter(getInterval(c).lo + 1, Set.empty)

  /** 按起点顺序的上一个内容片段。 */
  private[timeline] def prevContent(c: Content): Option[Content] = contentBefore(getInterval(c).lo, Set.empty)

  /** left 与 right 之间的转场，仅在两者确实相邻时返回。 */
  private[timeline] def transitionBetween(left: Content, right: Content): Option[Transition] =
    transitions.get(left).filter(_ => nextContent(left).exists(_ eq right))

  /** c 右侧紧邻的转场。 */
  def transitionAfter(c: Content): Option[Transition] = placedTransitionAfter(c)

  /** c 左侧紧邻的转场。 */
  def transitionBefore(c: Content): Option[Transition] = prevContent(c).flatMap(placedTransitionAfter)

  private def placedTransitionAfter(c: Content): Option[Transition] = transitions.get(c).filter(contains)

  /** 条目独占播放的终点。有右侧转场时内容只播到转场起点，转场则播满自己的区间。 */
  def soloEndOf(segment: Segment): Long = segment match {
    case c: Content =>
      val r = getInterval(c)
      placedTransitionAfter(c) match {
        case Some(t) => Math.min(r.hi, getInterval(t).lo)
        case None => r.hi
      }
    case _ => getInterval(segment).hi
  }

  /** 转场两侧的内容，左前右后。 */
  def transitionSides(t: Transition): Option[(Content, Content)] =
    transitions.getKey(t).filter(contains).flatMap(left => nextContent(left).map(right => (left, right)))

  /**
   * 尝试把内容片段放进指定区间，与已有内容的重叠自然成为转场。
   * @return 放得下时是 `(新轨道, 0)`；放不下时不加入，返回 `(本轨道, 最近的可容纳偏移)`
   */
  protected[timeline] def tryAdd(c: Content, r: Interval, origin: Long): (Track, Long) = {
    if (canPlaceAt(c, r)) (put(c, r, origin).syncAround(c), 0L) else (this, getShift(r))
  }

  /** 把内容片段放到指定区间。放不下时抛 IllegalArgumentException。要求片段不在本轨道上。 */
  protected[timeline] def addOrThrow(c: Content, r: Interval, origin: Long): Track = {
    if (!canPlaceAt(c, r)) throw new IllegalArgumentException("目标区间无法放置内容: " + r)
    put(c, r, origin).syncAround(c)
  }

  private def put(element: Segment, r: Interval, origin: Long): Track =
    derived(layout.updated(r, element), element match {
      case c: Clip => segToOrigin.updated(c, origin)
      case _ => segToOrigin
    })

  private def drop(element: Segment): Track =
    derived(layout.removedValue(element), element match {
      case c: Clip => segToOrigin.removed(c)
      case _ => segToOrigin
    })

  private def canPlaceAt(c: Content, r: Interval): Boolean = canPlaceAt(c, r, Set.empty)

  private[timeline] def canPlaceAt(c: Content, r: Interval, skip: Set[Segment]): Boolean = {
    if (r.isEmpty) return false
    val left = contentBefore(r.lo, skip)
    val right = contentAtOrAfter(r.lo, skip)
    // 与左邻居重叠时必须交叉，否则新条目会被左邻居包含
    val leftOk = left.forall { l =>
      val lr = getInterval(l)
      val crosses = lr.hi <= r.lo || (lr.lo < r.lo && lr.hi < r.hi && canTransition(l, c))
      crosses && contentBefore(lr.lo, skip).forall(ll => getInterval(ll).hi <= r.lo)
    }
    if (!leftOk) return false
    // 与右邻居重叠时必须交叉
    val rightOk = right.forall { x =>
      val rr = getInterval(x)
      val crosses = r.hi <= rr.lo || (r.lo < rr.lo && r.hi < rr.hi && canTransition(c, x))
      crosses && contentAtOrAfter(rr.lo + 1, skip).forall(rn => r.hi <= getInterval(rn).lo)
    }
    if (!rightOk) return false
    // 新条目把原来的相邻对拆成了隔项，左右邻居之间从此不能再重叠
    (left, right) match {
      case (Some(l), Some(x)) => getInterval(l).hi <= getInterval(x).lo
      case _ => true
    }
  }

  /**
   * 起点小于 time 的最后一个内容片段，跳过 skip 里的条目。
   */
  private def contentBefore(time: Long, skip: Set[Segment]): Option[Content] = {
    var bound = Interval.at(time)
    var entry = intervalToSeg.rangeUntil(bound).lastOption
    while (entry.isDefined) {
      val current = entry.get
      current._2 match {
        case x: Content if !isBlock(x) && !skip.contains(x) => return Some(x)
        case _ =>
      }
      bound = current._1
      entry = intervalToSeg.rangeUntil(bound).lastOption
    }
    None
  }

  /** 起点不小于 time 的第一个内容片段，跳过 skip 里的条目。 */
  private def contentAtOrAfter(time: Long, skip: Set[Segment]): Option[Content] = {
    intervalToSeg.rangeFrom(Interval.at(time)).iterator
      .map(_._2)
      .collectFirst { case x: Content if !isBlock(x) && !skip.contains(x) => x }
  }

  private def syncAround(c: Content): Track = {
    val t = prevContent(c).map(syncTransition).getOrElse(this)
    t.syncTransition(c)
  }

  private def syncTransition(left: Content): Track = {
    val old = transitions.get(left)
    nextContent(left) match {
      case None =>
        // 右邻居暂时缺席，索引项留着等它回来，表里的条目先摘掉
        unplaceTransition(left)
      case Some(right) =>
        val lo = getInterval(right).lo
        val hi = getInterval(left).hi
        if (lo < hi) {
          old.filter(contains) match {
            case Some(current) =>
              val r = getInterval(current)
              // 已在对应区间时无需改动
              if (r.lo == lo && r.hi == hi) this else put(current, lo ~~ hi, lo)
            case None =>
              val tr = old.getOrElse(newTransition(left, right))
              put(tr, lo ~~ hi, lo).indexTransition(left, tr)
          }
        } else if (old.isEmpty) {
          this
        } else {
          // 两侧内容都在却不再重叠，表里的条目与索引项都要摘掉
          old.filter(contains).map(drop).getOrElse(this).unindexTransition(left)
        }
    }
  }

  private def unplaceTransition(c: Content): Track =
    transitions.get(c).filter(contains).map(drop).getOrElse(this)

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
    var t = drop(c).unplaceTransition(c)
    (p, nextContent(c)) match {
      case (Some(left), Some(right)) =>
        val pr = t.getInterval(left)
        val nr = t.getInterval(right)
        if (nr.lo < pr.hi && !(pr.lo < nr.lo && pr.hi < nr.hi)) {
          val target = Math.max(nr.lo, pr.hi)
          if (target < nr.hi) {
            t = t.put(right, target ~~ nr.hi, t.getOrigin(right))
          }
        }
      case _ =>
    }
    p.map(t.syncTransition).getOrElse(t)
  }

  /**
   * 删除转场，即消除两侧内容的重叠。无法在不破坏布局的前提下消除时放弃本次删除，原样返回本实例。
   */
  private def removeTransition(t: Transition): Track = {
    val sides = transitionSides(t)
    if (sides.isEmpty) return this
    val (left, right) = sides.get
    val tr = getInterval(t)
    val lr = getInterval(left)
    val rr = getInterval(right)
    val half = (tr.hi - tr.lo) / 2
    val lower = Math.max(Math.max(lr.lo + 1, minStartOf(right)), endLower(left))
    var boundary = lr.hi - half
    boundary = Math.max(boundary, lower)
    boundary = Math.min(boundary, rr.hi - 1)
    nextContent(right).foreach(rn => boundary = Math.min(boundary, getInterval(rn).lo - 1))
    // 右后继的上限把交界点压回下界之下时两侧内容不再交叉，消除重叠无解，放弃本次删除
    if (boundary < lower || boundary <= lr.lo || boundary >= rr.hi) return this
    val next = drop(t)
      .put(left, lr.lo ~~ boundary, getOrigin(left))
      .put(right, boundary ~~ rr.hi, getOrigin(right))
    next.syncAround(left)
  }

  /** 移除这批片段，返回新版本。不在本轨道的片段会被忽略。 */
  protected[timeline] def removeAll(segments: Iterable[Segment]): Track = {
    var t = this
    for (s <- segments) {
      t = t.remove(s)
    }
    t
  }

  /** 在 time 处把内容片段一分为二。time 不落在内容片段的区间内部时原样返回本实例。 */
  protected[timeline] def split(time: Long): Track = {
    segmentAt(time) match {
      case Some(c: Content) =>
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
  protected[timeline] def setStart(segments: Iterable[Segment], deltaTime: Long): Track = {
    var t = this
    for (s <- segments) {
      if (t.contains(s)) t = t.setStart(s, deltaTime)
    }
    t
  }

  protected[timeline] def setStart(segment: Segment, deltaTime: Long): Track = segment match {
    case t: Transition => setTransitionStart(t, deltaTime)
    case c: Content => setContentStart(c, deltaTime)
  }

  /** 裁切一组片段的结束边缘（各自起点不变）。 */
  protected[timeline] def setEnd(segments: Iterable[Segment], deltaTime: Long): Track = {
    var t = this
    for (s <- segments) {
      if (t.contains(s)) t = t.setEnd(s, deltaTime)
    }
    t
  }

  protected[timeline] def setEnd(segment: Segment, deltaTime: Long): Track = segment match {
    case t: Transition => setTransitionEnd(t, deltaTime)
    case c: Content => setContentEnd(c, deltaTime)
  }

  private def setContentStart(c: Content, deltaTime: Long): Track = {
    val r = getInterval(c)
    val lower = startLower(c)
    val upper = startUpper(c)
    if (lower > upper) return this
    val target = Math.max(lower, Math.min(upper, r.lo + deltaTime))
    if (target == r.lo) return this
    put(c, target ~~ r.hi, getOrigin(c)).syncAround(c)
  }

  private def setContentEnd(c: Content, deltaTime: Long): Track = {
    val r = getInterval(c)
    val lower = endLower(c)
    val upper = endUpper(c)
    if (lower > upper) return this
    val target = Math.max(lower, Math.min(upper, r.hi + deltaTime))
    if (target == r.hi) return this
    put(c, r.lo ~~ target, getOrigin(c)).syncAround(c)
  }

  private def setTransitionStart(t: Transition, deltaTime: Long): Track = transitionSides(t) match {
    case Some((_, right)) => setContentStart(right, deltaTime)
    case None => this
  }

  private def setTransitionEnd(t: Transition, deltaTime: Long): Track = transitionSides(t) match {
    case Some((left, _)) => setContentEnd(left, deltaTime)
    case None => this
  }

  private def startLower(c: Content): Long = {
    val r = getInterval(c)
    var lower = Math.max(0L, minStartOf(c))
    prevContent(c) match {
      case Some(p) =>
        val pr = getInterval(p)
        val cap = if (canTransition(p, c) && r.hi > pr.hi) pr.lo + 1 else pr.hi
        lower = Math.max(lower, cap)
        prevContent(p).foreach(pp => lower = Math.max(lower, getInterval(pp).hi))
      case None =>
    }
    lower
  }

  private def startUpper(c: Content): Long = {
    val r = getInterval(c)
    var upper = r.hi - 1
    nextContent(c).foreach { n =>
      val nr = getInterval(n)
      if (nr.lo > r.lo && r.hi > nr.lo) {
        upper = Math.min(upper, nr.lo - 1)
      }
    }
    upper
  }

  private def endLower(c: Content): Long = {
    val r = getInterval(c)
    var lower = r.lo + 1
    prevContent(c).foreach { p =>
      val pr = getInterval(p)
      if (pr.hi > r.lo) {
        lower = Math.max(lower, pr.hi + 1)
      }
    }
    lower
  }

  private def endUpper(c: Content): Long = {
    val r = getInterval(c)
    var upper = maxEndOf(c)
    nextContent(c).foreach { n =>
      val nr = getInterval(n)
      val cap = if (canTransition(c, n)) nr.hi - 1 else nr.lo
      upper = Math.min(upper, cap)
      nextContent(n).foreach(nn => upper = Math.min(upper, getInterval(nn).lo))
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
    if (sides.isEmpty) return this
    val (left, right) = sides.get
    val lr = getInterval(left)
    val rr = getInterval(right)
    // 两侧同步移动，只需保证移动后仍交叉且不越过各自的素材与邻居约束
    val minShift = Math.max(
      Math.max(Math.max(minStartOf(right) - rr.lo, lr.lo + 1 - rr.lo), endLower(left) - lr.hi),
      lr.lo + 1 - lr.hi)
    var maxShift = Math.min(maxEndOf(left) - lr.hi, rr.hi - lr.hi - 1)
    nextContent(right).foreach(rn => maxShift = Math.min(maxShift, getInterval(rn).lo - 1 - rr.lo))
    if (minShift > maxShift) return this
    val applied = Math.max(minShift, Math.min(maxShift, delta))
    if (applied == 0) return this
    put(left, lr.lo ~~ (lr.hi + applied), getOrigin(left))
      .put(right, (rr.lo + applied) ~~ rr.hi, getOrigin(right))
      .syncAround(left)
  }

  private def derived(layout: Track.Layout,
                      segToOrigin: Map[Clip, Long]): Track =
    new Track(index, blockSegment, layout, segToOrigin, transitions)

  private def withTransitions(transitions: Track.TransitionIndex): Track =
    new Track(index, blockSegment, layout, segToOrigin, transitions)

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

  private def noSegment(interval: Interval): Boolean =
    !intersectingEntries(intervalToSeg, interval).hasNext

  /** interval 内是否没有被 ignore 之外的片段占用。 */
  def isFree(interval: Interval, ignore: Set[Segment]): Boolean = {
    intersectingEntries(intervalToSeg, interval).forall { case (_, s) => ignore.contains(s) }
  }

  protected[timeline] def probeSetStart(segments: Iterable[Segment], forward: Boolean): Long = {
    val offsets = segments.iterator
      .filter((s: Segment) => contains(s))
      .map {
        case t: Transition => probeTransitionStart(t, forward)
        case c: Content => probeContentStart(c, forward)
      }
    // 各成员偏移与请求同号，正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(Long.MaxValue) else offsets.maxOption.getOrElse(Long.MinValue)
  }

  protected[timeline] def probeSetEnd(segments: Iterable[Segment], forward: Boolean): Long = {
    val offsets = segments.iterator
      .filter((s: Segment) => contains(s))
      .map {
        case t: Transition => probeTransitionEnd(t, forward)
        case c: Content => probeContentEnd(c, forward)
      }
    // 各成员偏移与请求同号，正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(Long.MaxValue) else offsets.maxOption.getOrElse(Long.MinValue)
  }

  private def probeContentStart(c: Content, forward: Boolean): Long = {
    val r = getInterval(c)
    if (forward) Math.max(startUpper(c) - r.lo, 0) else Math.min(startLower(c) - r.lo, 0)
  }

  private def probeContentEnd(c: Content, forward: Boolean): Long = {
    val r = getInterval(c)
    if (forward) Math.max(endUpper(c) - r.hi, 0) else Math.min(endLower(c) - r.hi, 0)
  }

  private def probeTransitionStart(t: Transition, forward: Boolean): Long =
    transitionSides(t).map(s => probeContentStart(s._2, forward)).getOrElse(0L)

  private def probeTransitionEnd(t: Transition, forward: Boolean): Long =
    transitionSides(t).map(s => probeContentEnd(s._1, forward)).getOrElse(0L)

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

  protected[timeline] def probeMove(segments: Iterable[Segment], forward: Boolean): Long = {
    val noBlock: Long = if (forward) Long.MaxValue else Long.MinValue // 该方向无障碍，视作无界
    val moving = segments.toSet
    // 两侧内容都不动的转场要带着它们一起平移，得在摘掉整组后的布局上单独探测，按需构造
    lazy val bare: Track = removeAll(segments)
    val offsets = moving.iterator
      .filter((s: Segment) => contains(s))
      .map {
        case t: Transition =>
          transitionSides(t) match {
            case Some((left, right)) if !moving.contains(left) && !moving.contains(right) =>
              bare.probeShiftOf(left, right, getInterval(left), getInterval(right),
                maxEndOf(left), minStartOf(right), forward)
            case _ =>
              // 至少一侧内容也在搬运范围内时，转场随内容重建，不构成独立限制
              noBlock
          }
        case c: Content => probeMoveOf(c, getInterval(c), moving, forward)
      }
    // 各成员偏移与请求同号，正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(noBlock) else offsets.maxOption.getOrElse(noBlock)
  }

  private def probeMoveOf(c: Content, r: Interval, moving: Set[Segment], forward: Boolean): Long = {
    if (forward) {
      var bound = Long.MaxValue
      nextContent(c).foreach { n =>
        val nr = getInterval(n)
        if (!moving.contains(n)) {
          bound = Math.min(bound,
            if (canTransition(c, n)) Math.min(nr.lo - 1 - r.lo, nr.hi - 1 - r.hi) else nr.lo - r.hi)
        }
        nextContent(n).foreach(nn => if (!moving.contains(nn)) bound = Math.min(bound, getInterval(nn).lo - r.hi))
      }
      Math.max(bound, 0)
    } else {
      var bound = -r.lo
      prevContent(c).foreach { p =>
        val pr = getInterval(p)
        if (!moving.contains(p)) {
          val reach =
            if (canTransition(p, c)) Math.min(pr.hi - r.lo, Math.max(pr.lo + 1 - r.lo, pr.hi + 1 - r.hi))
            else pr.hi - r.lo
          bound = Math.max(bound, reach)
        }
        prevContent(p).foreach(pp => if (!moving.contains(pp)) bound = Math.max(bound, getInterval(pp).hi - r.lo))
      }
      Math.min(bound, 0)
    }
  }

  private def probeShiftOf(left: Content, right: Content, lr: Interval, rr: Interval,
                           hiCap: Long, loCap: Long, forward: Boolean): Long = {
    def skip(x: Content): Boolean = x.eq(left) || x.eq(right)
    if (forward) {
      var bound = hiCap - lr.hi
      val n = intervalToSeg.rangeFrom(Interval.at(rr.lo)).iterator.map(_._2)
        .collectFirst { case x: Content if !isBlock(x) && !skip(x) => x }
      n.foreach(x => bound = Math.min(bound, getInterval(x).lo - rr.hi))
      Math.max(bound, 0)
    } else {
      var bound = Math.max(loCap - rr.lo, lr.lo + 1 - lr.hi)
      val p = intervalToSeg.rangeUntil(Interval.at(lr.lo)).iterator.map(_._2)
        .collect { case x: Content if !isBlock(x) && !skip(x) => x }
        .toSeq.lastOption
      p.foreach(x => bound = Math.max(bound, getInterval(x).hi - lr.lo))
      Math.min(bound, 0)
    }
  }

  /** 包含 time 的条目；落在空隙中时返回临时物化的 [[Gap]]。 */
  def get(time: Long): Element = segmentAt(time).getOrElse(
    Gap(contentBefore(time, Set.empty), contentAtOrAfter(time, Set.empty)))

  /** time 所在条目（片段、转场或空隙）的区间。重叠时取区间最短的条目。 */
  def intervalAt(time: Long): Interval = getInterval(get(time))

  /** time 是否落在某个内容片段的区间内部，即能否在此分割。 */
  def canSplit(time: Long): Boolean = segmentAt(time) match {
    case Some(c: Content) =>
      val r = layout.reverse(c)
      time > r.lo && time < r.hi
    case _ => false
  }

  /** 起点顺序上紧随其后的条目。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def nextOf(segment: Segment): Option[Segment] = sourceAtOrAfter(getInterval(segment).hi)

  /** 起点顺序上紧邻其前的条目。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def prevOf(segment: Segment): Option[Segment] = sourceBefore(getInterval(segment).lo)

  /** 起点不小于 time 的首个片段。 */
  private[timeline] def sourceAtOrAfter(time: Long): Option[Segment] =
    intervalToSeg.rangeFrom(Interval.at(time)).iterator.map(_._2).nextOption()

  /** 起点小于 time 的最后一个片段。 */
  private[timeline] def sourceBefore(time: Long): Option[Segment] =
    lastBefore(intervalToSeg, time).map(_._2)

  /** 与 interval 有公共点的用户片段，按区间升序；返回快照。 */
  def getIntersecting(interval: Interval): Seq[Segment] = {
    intersectingEntries(intervalToSeg, interval).map(_._2).filterNot(isBlock).toList
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

  /** 轨道上的用户片段；阻挡片段对遍历不可见。 */
  override def iterator: Iterator[Segment] = {
    intervalToSeg.valuesIterator.filterNot(isBlock)
  }

  /** 调试用摘要，按时间顺序列出片段，内容附带 origin 与素材终点。 */
  override def toString: String = {
    val parts = intervalToSeg.iterator.filterNot((_, s) => isBlock(s)).map { case (r, s) =>
      s match {
        case c: Content =>
          c.displayName + "=[" + r.lo + "," + Track.timeText(r.hi) + ") o=" + getOrigin(c) + " m=" + Track.timeText(maxEndOf(c))
        case t: Transition =>
          t.displayName + "=[" + r.lo + "," + Track.timeText(r.hi) + ")"
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

  /** 包含 time 的片段。重叠时取区间最短的那个，它一定是转场。 */
  private def segmentAt(time: Long): Option[Segment] = {
    // 布局不变量（放置约束见 [[canPlaceAt]]，平移与裁切的上下界计算与之配套）使条目按 (lo, hi)
    // 排序后 hi 不减，覆盖 time 的条目因而是 lo ≤ time 的条目末尾的连续几个，反向扫描
    // 遇到 hi ≤ time 的条目即可停止，其之前的条目 hi 更短，不可能覆盖 time
    var best: Option[Segment] = None
    var bestLen = Long.MaxValue
    var current = lastAtOrBefore(Interval.upTo(time))
    while (current.isDefined) {
      val (r, s) = current.get
      if (r.hi <= time) return best
      if (r.hi - r.lo < bestLen) {
        best = Some(s)
        bestLen = r.hi - r.lo
      }
      current = intervalToSeg.maxBefore(r)
    }
    best
  }

  /** 与 interval 有公共点的条目，按区间升序。 */
  private def intersectingEntries(bt: immutable.TreeMap[Interval, Segment], interval: Interval): Iterator[(Interval, Segment)] = {
    if (interval.isEmpty) {
      Iterator.empty
    } else {
      bt.rangeUntil(Interval.at(interval.hi)).iterator
        .dropWhile(_._1.hi <= interval.lo)
    }
  }

  private def lastBefore(bt: immutable.TreeMap[Interval, Segment], time: Long): Option[(Interval, Segment)] =
    bt.maxBefore(Interval.at(time))

  /** 键不大于 bound 的最后一个条目。 */
  private def lastAtOrBefore(bound: Interval): Option[(Interval, Segment)] =
    intervalToSeg.get(bound) match {
      case Some(s) => Some((bound, s))
      case None => intervalToSeg.maxBefore(bound)
    }
}

object Track {
  private type Layout = BiMap[Interval, Segment, TreeMap[Interval, Segment], Map[Segment, Interval]]

  private type TransitionIndex = BiMap[Content, Transition, Map[Content, Transition], Map[Transition, Content]]

  /** 新建空轨道。 */
  private[timeline] def apply(index: Int): Track = {
    val blockSegment: Segment = new Boundless(new BlockSource)
    val layout = BiMap(TreeMap.empty[Interval, Segment], Map.empty[Segment, Interval])
      .updated(Long.MinValue ~~ 0L, blockSegment)
    new Track(index, blockSegment, layout,
      Map.empty[Clip, Long],
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
