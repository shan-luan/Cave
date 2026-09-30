package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{BlockSource, Content, Element, Frame, Gap, Segment, Transition}

import java.io.Serializable
import java.util
import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.immutable.TreeMap
import scala.jdk.CollectionConverters.*

/**
 * 轨道。只存片段（[[Segment]]），每条带自己的区间与片段内偏移 origin。空隙不存储，
 * [[get]] 与 [[rangeAt]] 落进空隙时临时物化一个 [[Gap]] 返回。
 *
 * 转场是相邻两块内容的重叠区，由内容布局就地派生，setter 不直接改它。
 * 内容保留自己的完整区间，与转场同放一张按区间索引的表。
 * 转场另有两张互反的索引，正向由左内容查到转场，反向由转场查到左内容，因此查询转场的两侧不必遍历正向表。
 *
 * 三条不变量。相邻内容交叉重叠且起点终点都不同，否则转场的键会和某一侧内容相同而被覆盖。
 * 隔项不重叠，任意时刻最多两块内容交叠，转场因此只定义在相邻对上。转场区间恒等于 `[next.lo, cur.hi)`。
 *
 * 不可变，每次编辑返回新实例。
 */
@SerialVersionUID(1L)
final class Track private (val timeline: Timeline, val index: Int,
                           private val blockSegment: Segment[Frame],
                           private val byTime: immutable.TreeMap[Interval, Segment[?]],
                           private val placements: Map[Segment[?], Interval],
                           private val origins: Map[Segment[?], Long],
                           private val rightTransitions: Map[Content[?], Transition[?]],
                           private val transitionLefts: Map[Transition[?], Content[?]]) extends Serializable with java.lang.Iterable[Segment[?]] {

  /** 是否是占据 0 点左侧的阻挡片段。它只提供左边界，对遍历不可见。 */
  private def isBlock(segment: Segment[?]): Boolean = segment.eq(blockSegment)

  /** 是否没有用户内容，阻挡片段不算。 */
  protected[timeline] def isEmpty: Boolean = byTime.valuesIterator.forall(isBlock)

  /** 片段占用的区间。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def getRange(segment: Segment[?]): Interval = {
    require(placements.contains(segment))
    placements(segment)
  }

  /** 片段的 0 秒在时间轴中的位置。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def getOrigin(segment: Segment[?]): Long = {
    require(origins.contains(segment))
    origins(segment)
  }

  def contains(segment: Segment[?]): Boolean = placements.contains(segment)

  /** 最后一个片段的终点；没有片段时为 0。 */
  lazy val length: Long = byTime.iterator.filter { case (_, s) => !isBlock(s) }.map(_._1.hi).maxOption.getOrElse(0L)

  /** 按起点顺序的下一个内容片段；没有时返回 null。 */
  private[timeline] def nextContent(c: Content[?]): Content[?] = contentAtOrAfter(getRange(c).lo + 1, null)

  /** 按起点顺序的上一个内容片段；没有时返回 null。 */
  private[timeline] def prevContent(c: Content[?]): Content[?] = contentBefore(getRange(c).lo, null)

  /** left 与 right 之间的转场，仅在两者确实相邻时返回；否则 null。 */
  private[timeline] def transitionBetween(left: Content[?], right: Content[?]): Transition[?] = {
    rightTransitions.get(left) match {
      case Some(t) if nextContent(left) eq right => t
      case _ => null
    }
  }

  /** c 右侧紧邻的转场；没有时返回 null。 */
  def transitionAfter(c: Content[?]): Transition[?] = rightTransitions.get(c).orNull

  /** c 左侧紧邻的转场；没有时返回 null。 */
  def transitionBefore(c: Content[?]): Transition[?] = {
    val p = prevContent(c)
    if (p == null) null else rightTransitions.get(p).orNull
  }

  /** 条目独占播放的终点。有右侧转场时内容只播到转场起点，转场则播满自己的区间。 */
  def soloEndOf(segment: Segment[?]): Long = segment match {
    case c: Content[?] =>
      val r = getRange(c)
      rightTransitions.get(c) match {
        case Some(t) => Math.min(r.hi, getRange(t).lo)
        case None => r.hi
      }
    case _ => getRange(segment).hi
  }

  /** 转场两侧的内容，左前右后；转场不在本轨道时返回 null。 */
  def transitionSides(t: Transition[?]): (Content[?], Content[?]) = {
    val left = transitionLefts.getOrElse(t, null)
    if (left == null || !contains(left)) null else (left, nextContent(left))
  }

  /**
   * 尝试把内容片段放进指定区间，与已有内容的重叠自然成为转场。
   * @return 放得下时是 `(新轨道, 0)`；放不下时不加入，返回 `(本轨道, 最近的可容纳偏移)`
   */
  protected[timeline] def tryAdd(segment: Segment[?], r: Interval, origin: Long): (Track, Long) = segment match {
    case _: Transition[?] =>
      throw new IllegalArgumentException("不允许手动添加转场片段")
    case c: Content[?] =>
      if (canPlaceAt(c, r)) (put(c, r, origin).syncAround(c), 0L) else (this, getShift(r))
  }

  /** 把内容片段放到指定区间。放不下时抛 IllegalArgumentException。要求片段不在本轨道上。 */
  protected[timeline] def addOrThrow(segment: Segment[?], r: Interval, origin: Long): Track = segment match {
    case _: Transition[?] =>
      throw new IllegalArgumentException("不允许手动添加转场片段")
    case c: Content[?] =>
      if (!canPlaceAt(c, r)) throw new IllegalArgumentException("目标区间无法放置内容: " + r)
      put(c, r, origin).syncAround(c)
  }

  /** 把不在表里的元素放到指定区间。 */
  private def put(element: Segment[?], r: Interval, origin: Long): Track =
    derived(byTime.updated(r, element), placements.updated(element, r), origins.updated(element, origin))

  /** 把元素从表里摘掉，留下的区间成为空隙。 */
  private def drop(element: Segment[?]): Track =
    derived(byTime.removed(placements(element)), placements.removed(element), origins.removed(element))

  /** 把已在表里的元素挪到新区间。 */
  private def rekey(element: Segment[?], r: Interval, origin: Long): Track =
    derived(byTime.removed(placements(element)).updated(r, element),
      placements.updated(element, r),
      origins.updated(element, origin))

  private def canPlaceAt(c: Content[?], r: Interval): Boolean = canPlaceAt(c, r, null)

  /**
   * 放入后轨道是否仍满足三条不变量，要求该内容不在本轨道上。skip 里的条目不参与检查，
   * 整组搬运与粘贴时用它忽略同伴。合法性只由新条目改变，检查因此只看它的邻居与隔项。
   */
  private[timeline] def canPlaceAt(c: Content[?], r: Interval, skip: util.Collection[Segment[?]]): Boolean = {
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
   * 从末尾往前找，末尾的转场与阻挡片段通常只有一两个，因此是 O(log n) 而不是扫描整个前缀。
   */
  private def contentBefore(time: Long, skip: util.Collection[Segment[?]]): Content[?] = {
    var bound = at(time)
    var entry = byTime.rangeUntil(bound).lastOption
    while (entry.isDefined) {
      val current = entry.get
      current._2 match {
        case x: Content[?] if !isBlock(x) && (skip == null || !skip.contains(x)) => return x
        case _ =>
      }
      bound = current._1
      entry = byTime.rangeUntil(bound).lastOption
    }
    null
  }

  /** 起点不小于 time 的第一个内容片段，跳过 skip 里的条目；没有时返回 null。 */
  private def contentAtOrAfter(time: Long, skip: util.Collection[Segment[?]]): Content[?] = {
    byTime.rangeFrom(at(time)).iterator
      .map(_._2)
      .collectFirst { case x: Content[?] if !isBlock(x) && (skip == null || !skip.contains(x)) => x }
      .orNull
  }

  /**
   * 对齐 c 与它左右邻居之间的转场条目，c 必须在本轨道上。
   * 转场只定义在相邻内容对上，改一个内容最多影响它与左邻居、它与右邻居这两条。
   */
  private def syncAround(c: Content[?]): Track = {
    val p = prevContent(c)
    val t = if (p == null) this else syncTransition(p)
    t.syncTransition(c)
  }

  /**
   * 把 left 与它右邻居之间的转场条目对齐到当前布局，重叠建转场的规则只写在这里。
   * 重叠关系没变的转场复用原对象，界面上的对象身份因此稳定。
   * 右邻居暂时不在轨道上时保留索引项，它回来（整组搬运、撤销）时才能复用同一个对象。
   */
  private def syncTransition(left: Content[?]): Track = {
    val old = rightTransitions.getOrElse(left, null)
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
          // 这一格已经是对应的转场时无需改动。被内容顶掉（区间键相撞）时要重新放回去，
          // 撞掉它的那个内容正是本对的右内容，所以只有本对能把它重建出来
          if (current.lo == lo && current.hi == hi && (byTime.getOrElse(current, null) eq old)) this
          else rekey(old, lo ~~ hi, lo)
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

  /**
   * 内容离开轨道后摘掉它在表里的转场条目，两张索引都留着，它被放回来时仍是同一个转场对象。
   */
  private def unplaceTransition(c: Content[?]): Track = {
    val old = rightTransitions.getOrElse(c, null)
    if (old != null && contains(old)) drop(old) else this
  }

  /** 记下 left 右侧的转场，两张互反的索引一起更新。 */
  private def indexTransition(left: Content[?], tr: Transition[?]): Track =
    withTransitions(rightTransitions.updated(left, tr), transitionLefts.updated(tr, left))

  /** 忘掉 left 右侧的转场，两张互反的索引一起更新。 */
  private def unindexTransition(left: Content[?]): Track = {
    val tr = rightTransitions.getOrElse(left, null)
    withTransitions(rightTransitions.removed(left),
      if (tr == null) transitionLefts else transitionLefts.removed(tr))
  }

  /** 移除片段。片段不在本轨道时原样返回本实例。 */
  protected[timeline] def remove(segment: Segment[?]): Track = {
    if (!contains(segment)) {
      this
    } else {
      segment match {
        case t: Transition[?] => removeTransition(t)
        case c: Content[?] => removeContent(c)
      }
    }
  }

  /**
   * 删除内容。它的左右邻居从隔项变成相邻，两者若重叠就必须能构成转场，否则把右侧推到左侧之后。
   */
  private def removeContent(c: Content[?]): Track = {
    val p = prevContent(c)
    val n = nextContent(c)
    var t = drop(c).unplaceTransition(c)
    if (p != null && n != null) {
      val pr = t.getRange(p)
      val nr = t.getRange(n)
      if (nr.lo < pr.hi && !(pr.lo < nr.lo && pr.hi < nr.hi)) {
        val target = Math.max(nr.lo, pr.hi)
        if (target < nr.hi) {
          t = t.rekey(n, target ~~ nr.hi, t.getOrigin(n))
        }
      }
    }
    if (p == null) t else t.syncTransition(p)
  }

  /**
   * 删除转场。转场是重叠区的派生条目，只摘掉条目会被重算立刻建回来，所以真正的删除是消除重叠：
   * 两侧内容各让一半，交界点落在原转场中心；越出两侧内容范围时放弃本次删除。
   */
  private def removeTransition(t: Transition[?]): Track = {
    val sides = transitionSides(t)
    if (sides == null) return this
    val (left, right) = sides
    val tr = getRange(t)
    val lr = getRange(left)
    val rr = getRange(right)
    val half = (tr.hi - tr.lo) / 2
    var boundary = lr.hi - half
    boundary = Math.max(boundary, Math.max(Math.max(lr.lo + 1, minStartOf(right)), endLower(left)))
    boundary = Math.min(boundary, rr.hi - 1)
    val rn = nextContent(right)
    if (rn != null) {
      boundary = Math.min(boundary, getRange(rn).lo - 1)
    }
    if (boundary <= lr.lo || boundary >= rr.hi) return this
    val next = drop(t)
      .rekey(left, lr.lo ~~ boundary, getOrigin(left))
      .rekey(right, boundary ~~ rr.hi, getOrigin(right))
    next.syncAround(left)
  }

  /** 移除这批片段，返回新版本。不在本轨道的片段会被忽略。 */
  protected[timeline] def removeAll(segments: util.Collection[Segment[?]]): Track = {
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
      case c: Content[?] =>
        val r = placements(c)
        if (time <= r.lo || time >= r.hi) return this
        val origin: Long = getOrigin(c)
        val right = c.duplicate().asInstanceOf[Content[?]]
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
  protected[timeline] def setStart(segments: util.Collection[Segment[?]], deltaTime: Long): Track = {
    var t = this
    for (s <- segments.asScala) {
      if (t.contains(s)) t = t.setStart(s, deltaTime)
    }
    t
  }

  protected[timeline] def setStart(segment: Segment[?], deltaTime: Long): Track = segment match {
    case t: Transition[?] => setTransitionStart(t, deltaTime)
    case c: Content[?] => setContentStart(c, deltaTime)
  }

  /** 裁切一组片段的结束边缘（各自起点不变）。 */
  protected[timeline] def setEnd(segments: util.Collection[Segment[?]], deltaTime: Long): Track = {
    var t = this
    for (s <- segments.asScala) {
      if (t.contains(s)) t = t.setEnd(s, deltaTime)
    }
    t
  }

  protected[timeline] def setEnd(segment: Segment[?], deltaTime: Long): Track = segment match {
    case t: Transition[?] => setTransitionEnd(t, deltaTime)
    case c: Content[?] => setContentEnd(c, deltaTime)
  }

  /** 移动内容起点。起点可以伸进左邻居形成转场。 */
  private def setContentStart(c: Content[?], deltaTime: Long): Track = {
    val r = getRange(c)
    val lower = startLower(c)
    val upper = startUpper(c)
    if (lower > upper) return this
    val target = Math.max(lower, Math.min(upper, r.lo + deltaTime))
    if (target == r.lo) return this
    rekey(c, target ~~ r.hi, getOrigin(c)).syncAround(c)
  }

  /** 移动内容终点。终点可以伸进右邻居形成转场。 */
  private def setContentEnd(c: Content[?], deltaTime: Long): Track = {
    val r = getRange(c)
    val lower = endLower(c)
    val upper = endUpper(c)
    if (lower > upper) return this
    val target = Math.max(lower, Math.min(upper, r.hi + deltaTime))
    if (target == r.hi) return this
    rekey(c, r.lo ~~ target, getOrigin(c)).syncAround(c)
  }

  /** 转场左缘在几何上就是右内容的起点，直接转成移动它。 */
  private def setTransitionStart(t: Transition[?], deltaTime: Long): Track = {
    val sides = transitionSides(t)
    if (sides == null) return this
    setContentStart(sides._2, deltaTime)
  }

  /** 转场右缘在几何上就是左内容的终点，直接转成移动它。 */
  private def setTransitionEnd(t: Transition[?], deltaTime: Long): Track = {
    val sides = transitionSides(t)
    if (sides == null) return this
    setContentEnd(sides._1, deltaTime)
  }

  /**
   * 内容起点的绝对下界。能与左邻居建转场且不会被它包含时允许重叠，否则贴住左邻居终点；
   * 还要受素材起点、时间轴 0 点与隔项不重叠限制。
   */
  private def startLower(c: Content[?]): Long = {
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

  /**
   * 内容起点的绝对上界。正常是自身终点前一微秒；与右邻居重叠时还必须留在对方起点之前，否则会被对方包含。
   */
  private def startUpper(c: Content[?]): Long = {
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

  /**
   * 内容终点的绝对下界。正常是自身起点后一微秒；与左邻居重叠时还必须留在对方终点之后，否则会被对方包含。
   */
  private def endLower(c: Content[?]): Long = {
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

  /**
   * 内容终点的绝对上界。能与右邻居建转场时允许重叠，但至少给对方留一微秒，否则贴住右邻居起点；
   * 还要受素材终点与隔项不重叠限制。
   */
  private def endUpper(c: Content[?]): Long = {
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

  private def canTransition(from: Content[?], to: Content[?]): Boolean = {
    from.asInstanceOf[Content[Frame]].canCreateTransitionWith(to.asInstanceOf[Content[Frame]])
  }

  private def newTransition(from: Content[?], to: Content[?]): Transition[Frame] = {
    from.asInstanceOf[Content[Frame]].createTransition(to.asInstanceOf[Content[Frame]])
  }

  /**
   * 拖动 c 时会跟着动的邻居，即相邻且可建立转场的那些。它们的外缘与 c 的外缘重合，
   * 吸附忽略集必须包含它们，否则目标会被吸回正在移动的边缘。
   */
  def movingNeighbours(c: Content[?]): util.List[Content[?]] = {
    val out = new util.ArrayList[Content[?]](2)
    val cr = getRange(c)
    val p = prevContent(c)
    if (p != null && canTransition(p, c) && getRange(p).hi >= cr.lo) out.add(p)
    val n = nextContent(c)
    if (n != null && canTransition(c, n) && getRange(n).lo <= cr.hi) out.add(n)
    out
  }

  /** 整条转场刚性平移，两侧内容的边界同步移动同样的量，转场宽度不变。 */
  protected[timeline] def shiftTransition(t: Transition[?], delta: Long): Track = {
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
    rekey(left, lr.lo ~~ (lr.hi + applied), getOrigin(left))
      .rekey(right, (rr.lo + applied) ~~ rr.hi, getOrigin(right))
      .syncAround(left)
  }

  private def derived(byTime: immutable.TreeMap[Interval, Segment[?]],
                      placements: Map[Segment[?], Interval],
                      origins: Map[Segment[?], Long]): Track =
    new Track(timeline, index, blockSegment, byTime, placements, origins, rightTransitions, transitionLefts)

  /** 换上新的转场索引，其余保持。两张互反的索引必须一起换，否则 [[Track.transitionSides]] 会查到旧的一侧。 */
  private def withTransitions(rightTransitions: Map[Content[?], Transition[?]],
                              transitionLefts: Map[Transition[?], Content[?]]): Track =
    new Track(timeline, index, blockSegment, byTime, placements, origins, rightTransitions, transitionLefts)

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
        val obstacles = intersectingEntries(byTime, r.shift(s))
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
    !intersectingEntries(byTime, range).hasNext

  /** range 内是否没有被 ignore 之外的片段占用，ignore 为空时是完全空闲检查。 */
  def isFree(range: Interval, ignore: util.Collection[Segment[?]]): Boolean = {
    intersectingEntries(byTime, range).forall { case (_, s) => ignore.contains(s) }
  }

  /**
   * 探测起点沿指定方向可移动多少。forward 是裁头，只受自身素材长度限制；
   * 反向是伸头，受时间轴 0 点、origin 与前邻能否建转场限制。
   */
  protected[timeline] def probeSetStart(segments: util.Collection[Segment[?]], forward: Boolean): Long = {
    val offsets = segments.asScala.iterator
      .filter((s: Segment[?]) => contains(s))
      .map {
        case t: Transition[?] => probeTransitionStart(t, forward)
        case c: Content[?] => probeContentStart(c, forward)
      }
    // 各成员偏移与请求同号，正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(Long.MaxValue) else offsets.maxOption.getOrElse(Long.MinValue)
  }

  /**
   * 探测终点沿指定方向可移动多少。forward 是伸尾，受后继、后邻能否建转场与素材长度 maxEnd 限制；
   * 反向是裁尾，只受自身素材长度限制。
   */
  protected[timeline] def probeSetEnd(segments: util.Collection[Segment[?]], forward: Boolean): Long = {
    val offsets = segments.asScala.iterator
      .filter((s: Segment[?]) => contains(s))
      .map {
        case t: Transition[?] => probeTransitionEnd(t, forward)
        case c: Content[?] => probeContentEnd(c, forward)
      }
    // 各成员偏移与请求同号，正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(Long.MaxValue) else offsets.maxOption.getOrElse(Long.MinValue)
  }

  private def probeContentStart(c: Content[?], forward: Boolean): Long = {
    val r = getRange(c)
    if (forward) Math.max(startUpper(c) - r.lo, 0) else Math.min(startLower(c) - r.lo, 0)
  }

  private def probeContentEnd(c: Content[?], forward: Boolean): Long = {
    val r = getRange(c)
    if (forward) Math.max(endUpper(c) - r.hi, 0) else Math.min(endLower(c) - r.hi, 0)
  }

  /** 转场左缘即右内容的起点。 */
  private def probeTransitionStart(t: Transition[?], forward: Boolean): Long = {
    val sides = transitionSides(t)
    if (sides == null) 0L else probeContentStart(sides._2, forward)
  }

  /** 转场右缘即左内容的终点。 */
  private def probeTransitionEnd(t: Transition[?], forward: Boolean): Long = {
    val sides = transitionSides(t)
    if (sides == null) 0L else probeContentEnd(sides._1, forward)
  }

  /** 允许的最小起点，即片段的 0 秒不能越过时间轴 0 点。 */
  private def minStartOf(segment: Segment[?]): Long = {
    Math.max(0, getOrigin(segment))
  }

  private def maxEndOf(segment: Segment[?]): Long = {
    val duration = segment.getDuration
    if (duration == Long.MaxValue) Long.MaxValue else getOrigin(segment) + duration
  }

  /**
   * 探测整组沿指定方向可平移多少。整组刚性平移，成员彼此之间相对位置不变，因此只有成员与非成员
   * 之间新形成的相邻、隔项关系会变，逐个成员看它紧邻的两个位置就够，取最严者即上限。
   * 撞上可建转场的邻居时按重叠处理，可以吃到邻居只剩一微秒，但不能吞掉邻居或被邻居吞掉。
   * 左移另受时间轴 0 限制。
   */
  protected[timeline] def probeMove(segments: util.Collection[Segment[?]], forward: Boolean): Long = {
    val noBlock: Long = if (forward) Long.MaxValue else Long.MinValue // 该方向无障碍，视作无界
    val moving = segments.asScala.toSet
    // 两侧内容都不动的转场要带着它们一起平移，得在摘掉整组后的布局上单独探测，按需构造
    lazy val bare: Track = removeAll(segments)
    val offsets = moving.iterator
      .filter((s: Segment[?]) => contains(s))
      .map {
        case t: Transition[?] =>
          val sides = transitionSides(t)
          if (sides == null || moving.contains(sides._1) || moving.contains(sides._2)) {
            // 至少一侧内容也在搬运范围内时，转场随内容重建，不构成独立限制
            noBlock
          } else {
            val (left, right) = sides
            bare.probeShiftOf(left, right, getRange(left), getRange(right),
              maxEndOf(left), minStartOf(right), forward)
          }
        case c: Content[?] => probeMoveOf(c, getRange(c), moving, forward)
      }
    // 各成员偏移与请求同号，正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(noBlock) else offsets.maxOption.getOrElse(noBlock)
  }

  /**
   * 内容 c 沿指定方向能平移多少。r 是它移动前的区间，moving 是本次搬运的全部成员。
   * c 紧邻的位置上也是成员时两者一起平移、相对位置不变，约束改由隔一个位置上的内容决定，
   * 因此这里只看相邻与隔项两个位置，再往外不构成约束。
   */
  private def probeMoveOf(c: Content[?], r: Interval, moving: Set[Segment[?]], forward: Boolean): Long = {
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

  /**
   * 在摘掉两侧内容之后的布局上，转场随两侧同步平移能走多远。
   * hiCap 与 loCap 是两侧的素材边界，lr 与 rr 是它们移动前的区间。
   */
  private def probeShiftOf(left: Content[?], right: Content[?], lr: Interval, rr: Interval,
                           hiCap: Long, loCap: Long, forward: Boolean): Long = {
    def skip(x: Content[?]): Boolean = x.eq(left) || x.eq(right)
    if (forward) {
      var bound = hiCap - lr.hi
      val n = byTime.rangeFrom(at(rr.lo)).iterator.map(_._2)
        .collectFirst { case x: Content[?] if !isBlock(x) && !skip(x) => x }
        .orNull
      if (n != null) {
        bound = Math.min(bound, getRange(n).lo - rr.hi)
      }
      Math.max(bound, 0)
    } else {
      var bound = Math.max(loCap - rr.lo, lr.lo + 1 - lr.hi)
      val p = byTime.rangeUntil(at(lr.lo)).iterator.map(_._2)
        .collect { case x: Content[?] if !isBlock(x) && !skip(x) => x }
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
      var from = 0L
      for ((r, _) <- byTime.rangeTo(upTo(time))) {
        if (r.hi > from) from = r.hi
      }
      from ~~ nextStart(time)
    }
  }

  /** time 是否落在某个内容片段的区间内部，即能否在此分割。 */
  def canSplit(time: Long): Boolean = segmentAt(time) match {
    case c: Content[?] =>
      val r = placements(c)
      time > r.lo && time < r.hi
    case _ => false
  }

  /** 起点顺序上紧随其后的条目；没有时返回 null。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def nextOf(segment: Segment[?]): Segment[?] = sourceAtOrAfter(getRange(segment).hi)

  /** 起点顺序上紧邻其前的条目；没有时返回 null。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def prevOf(segment: Segment[?]): Segment[?] = sourceBefore(getRange(segment).lo)

  /** 起点不小于 time 的首个片段；没有时返回 null。 */
  private[timeline] def sourceAtOrAfter(time: Long): Segment[?] = {
    byTime.rangeFrom(at(time)).iterator.map(_._2).nextOption().orNull
  }

  /** 起点小于 time 的最后一个片段；没有时返回 null。 */
  private[timeline] def sourceBefore(time: Long): Segment[?] = {
    lastBefore(byTime, time) match {
      case null => null
      case (_, s) => s
    }
  }

  /** 与 range 有公共点的用户片段，按区间升序；返回快照。 */
  def getIntersecting(range: Interval): util.List[Segment[?]] = {
    intersectingEntries(byTime, range).map(_._2).filterNot(isBlock).toList.asJava
  }

  /** 生成片段在绝对时间 time 的帧。 */
  def frameAt(segment: Segment[?], time: Long): Frame = {
    val f = segment.get(time - getOrigin(segment), this)
    if (f == null) null else f.withTime(time)
  }

  /** 把片段同步到绝对时间 time。 */
  def syncAt(segment: Segment[?], time: Long): Unit = {
    segment.sync(time - getOrigin(segment), this)
  }

  /** 轨迹线程，按轨道索引唯一，由 [[Timeline]] 持有，故轨道换版本时它保持不变。 */
  def getWorker: timeline.TrackWorker = timeline.getWorker(index)

  /** 轨道上的用户片段；阻挡片段对遍历不可见。 */
  override def iterator(): util.Iterator[Segment[?]] = {
    byTime.valuesIterator.filterNot(isBlock).asJava
  }

  /** 调试用摘要，按时间顺序列出片段，内容附带 origin 与素材终点。 */
  override def toString: String = {
    val parts = byTime.iterator.filterNot((_, s) => isBlock(s)).map { case (r, s) =>
      s match {
        case c: Content[?] =>
          c.displayName + "=[" + r.lo + "," + Track.timeText(r.hi) + ") o=" + origins(c) + " m=" + Track.timeText(maxEndOf(c))
        case t: Transition[?] =>
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
          val a = byTime.iterator.toIndexedSeq
          val b = other.byTime.iterator.toIndexedSeq
          a.size == b.size && a.zip(b).forall { case ((ia, ea), (ib, eb)) =>
            ia == ib && entryEquals(ea, eb, other)
          }
        }
      case _ => false
    }
  }

  /**
   * 比较两个条目，类型与时长逐个比，再比 origin，区间作为键已经比过。
   * 按字段而不是按对象身份，是为了跟序列化快照这类不同实例做结构对比。
   */
  private def entryEquals(a: Segment[?], b: Segment[?], other: Track): Boolean = {
    Track.sourceEquals(a, b) && getOrigin(a) == other.getOrigin(b)
  }

  override def hashCode(): Int = {
    Integer.hashCode(index)
  }

  /** 包含 time 的片段，落在空隙中时返回 null。重叠时取区间最短的那个，它一定是转场。 */
  private def segmentAt(time: Long): Segment[?] = {
    var best: Segment[?] = null
    var bestLen = Long.MaxValue
    for ((r, s) <- byTime.rangeTo(upTo(time))) {
      if (r.hi > time && r.hi - r.lo < bestLen) {
        best = s
        bestLen = r.hi - r.lo
      }
    }
    best
  }

  /**
   * 与 range 有公共点的条目，按区间升序。条目之间不存在一个完整包住另一个的情况，
   * 相交的条目在起点序上因此是连续的一段。
   */
  private def intersectingEntries(bt: immutable.TreeMap[Interval, Segment[?]], range: Interval): Iterator[(Interval, Segment[?])] = {
    if (range.isEmpty) {
      Iterator.empty
    } else {
      bt.rangeUntil(at(range.hi)).iterator
        .dropWhile(_._1.hi <= range.lo)
    }
  }

  /** 起点不大于 time 的一切条目，作 rangeTo 的上界。 */
  private def upTo(time: Long): Interval = time ~~ Long.MaxValue

  /** time 处的空区间，作起点与 time 的分界。 */
  private def at(time: Long): Interval = time ~~ time

  /** 起点小于 time 的最后一个条目；没有时返回 null。 */
  private def lastBefore(bt: immutable.TreeMap[Interval, Segment[?]], time: Long): (Interval, Segment[?]) = {
    bt.rangeUntil(at(time)).toSeq.lastOption.orNull
  }

  /** 起点不小于 time 的首个片段的起点；没有时是时间轴尽头。 */
  private def nextStart(time: Long): Long = {
    byTime.rangeFrom(at(time)).iterator.nextOption().map(_._1.lo).getOrElse(Long.MaxValue)
  }
}

object Track {
  /** 新建空轨道。0 点左侧放阻挡片段，覆盖 [Long.MinValue, 0)，右侧全是空隙。 */
  private[timeline] def apply(timeline: Timeline, index: Int): Track = {
    val blockSegment: Segment[Frame] = new Content[Frame](new BlockSource)
    new Track(timeline, index, blockSegment,
      immutable.TreeMap[Interval, Segment[?]](Long.MinValue ~~ 0L -> blockSegment),
      Map[Segment[?], Interval](blockSegment -> (Long.MinValue ~~ 0L)),
      Map[Segment[?], Long](blockSegment -> 0L),
      Map.empty,
      Map.empty)
  }

  private final val MAX_SLIDE_STEPS = 10000

  private def timeText(time: Long): String = {
    if (time == Long.MaxValue) "∞" else time.toString
  }

  private def sourceEquals(a: Segment[?], b: Segment[?]): Boolean = {
    if (a.eq(b)) {
      true
    } else {
      a.getClass == b.getClass && a.getDuration == b.getDuration
    }
  }
}
