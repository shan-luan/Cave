package com.lomekwi.cave.timeline

import com.lomekwi.cave.collection.BiMap
import com.lomekwi.cave.pipeline.{BlockSource, EvalClock, Frame}
import com.lomekwi.cave.project.{Id, IdSource}
import com.lomekwi.cave.timeline.Timespan.*

import java.io.Serializable
import scala.collection.immutable
import scala.collection.immutable.TreeMap

/** 轨道。 */
@SerialVersionUID(1L)
case class Track private (index: Int,
                          private val segments: Map[Id, Segment],
                          private val layout: Track.Layout,
                          private val segToOrigin: Map[Id, Instant],//TODO:很多人似乎没法理解这是啥，也许应该改成更常见的入点概念。顺便，可以并入Segment?
                          private val transitions: Track.TransitionIndex) extends Serializable with Iterable[Id] {

  private def isBlock(id: Id): Boolean = id == Id.noId()

  def segment(id: Id): Segment = segments(id)

  def segmentOption(id: Id): Option[Segment] = segments.get(id)

  private def transitionOf(id: Id): Transition = segments(id).asInstanceOf[Transition]

  private def intervalToSeg: TreeMap[Interval, Id] = layout.forward

  /** 是否没有用户内容。 */
  override def isEmpty: Boolean = intervalToSeg.valuesIterator.forall(id => isBlock(id))

  /** 元素占用的区间。 */
  extension (e: Element)
    def interval: Interval = e match {
      case s: Segment => s.id.interval
      case Gap(before, after) =>
        val lo = before.map(id => id.interval.hi).getOrElse(Instant.Zero)
        val hi = after.map(id => id.interval.lo).getOrElse(Instant.MaxValue)
        lo ~~ hi
    }

  extension (id: Id)
    def interval: Interval = {
      require(layout.containsValue(id))
      layout.reverse(id)
    }

  /** 片段的 0 秒在时间轴中的位置。 */
  def getOrigin(segment: Segment): Instant = getOrigin(segment.id)

  def getOrigin(id: Id): Instant = segment(id) match {
    case _: Clip =>
      require(segToOrigin.contains(id))
      segToOrigin(id)
    case _ =>
      id.interval.lo
  }

  def contains(segment: Segment): Boolean = contains(segment.id)

  def contains(id: Id): Boolean = layout.containsValue(id)

  def length: Instant = intervalToSeg.last._1.hi

  /** 按起点顺序的下一个内容片段。 */
  private[timeline] def nextContent(c: Content): Option[Content] = contentAtOrAfter(c.id.interval.lo + 1.micros, Set.empty)

  /** 按起点顺序的上一个内容片段。 */
  private[timeline] def prevContent(c: Content): Option[Content] = contentBefore(c.id.interval.lo, Set.empty)

  /** left 与 right 之间的转场。 */
  private[timeline] def transitionBetween(left: Content, right: Content): Option[Transition] =
    transitions.get(left.id).filter(_ => nextContent(left).exists(_.id == right.id)).map(transitionOf)

  /** c 右侧紧邻的转场。 */
  def transitionAfter(c: Content): Option[Transition] = placedTransitionAfter(c)

  /** c 左侧紧邻的转场。 */
  def transitionBefore(c: Content): Option[Transition] = prevContent(c).flatMap(placedTransitionAfter)

  private def placedTransitionAfter(c: Content): Option[Transition] =
    transitions.get(c.id).filter(id => contains(id)).map(transitionOf)

  /** 带有后接转场的片段，其后接转场的起点。 */
  def soloEndOf(segment: Segment): Instant = segment match {
    case c: Content =>
      val r = c.id.interval
      placedTransitionAfter(c) match {
        case Some(t) => r.hi.min(t.interval.lo)
        case None => r.hi
      }
    case _ => segment.interval.hi
  }

  /** 转场两侧的内容，左前右后。 */
  def transitionSides(t: Transition): Option[(Content, Content)] =
    transitions.getKey(t.id).filter(id => contains(id)).flatMap { leftId =>
      contentAtOrAfter(leftId.interval.lo + 1.micros, Set.empty).map { right =>
        (segment(leftId).asInstanceOf[Content], right)
      }
    }

  protected[timeline] def add(c: Content, r: Interval, origin: Instant)(using ids: IdSource): Track =
    if (canPlaceAt(c, r)) put(c, r, origin).syncAround(c) else this

  private def put(element: Segment, r: Interval, origin: Instant): Track = {
    val id = element.id
    val origins = element match {
      case _: Clip => segToOrigin.updated(id, origin)
      case _ => segToOrigin
    }
    derived(layout.updated(r, id), origins, segments.updated(id, element))
  }

  private def dropFromLayout(id: Id): Track =
    derived(layout.removedValue(id), segToOrigin.removed(id), segments)

  private def removeSegment(id: Id): Track =
    derived(layout.removedValue(id), segToOrigin.removed(id), if (isBlock(id)) segments else segments.removed(id))

  private def canPlaceAt(c: Content, r: Interval): Boolean = canPlaceAt(c, r, Set.empty[Id])

  private[timeline] def canPlaceAt(c: Content, r: Interval, skip: Set[Id]): Boolean = {
    if (r.isEmpty) return false
    val left = contentBefore(r.lo, skip)
    val right = contentAtOrAfter(r.lo, skip)
    val leftOk = left.forall { l =>
      val lr = l.id.interval
      val crosses = lr.hi <= r.lo || (lr.lo < r.lo && lr.hi < r.hi && canTransition(l, c))
      crosses && contentBefore(lr.lo, skip).forall(ll => ll.id.interval.hi <= r.lo)
    }
    if (!leftOk) return false
    val rightOk = right.forall { x =>
      val rr = x.id.interval
      val crosses = r.hi <= rr.lo || (r.lo < rr.lo && r.hi < rr.hi && canTransition(c, x))
      crosses && contentAtOrAfter(rr.lo + 1.micros, skip).forall(rn => r.hi <= rn.id.interval.lo)
    }
    if (!rightOk) return false
    (left, right) match {
      case (Some(l), Some(x)) => l.id.interval.hi <= x.id.interval.lo
      case _ => true
    }
  }

  /** 起点小于 time 的最后一个内容片段，跳过 skip 里的条目。 */
  private def contentBefore(time: Instant, skip: Set[Id]): Option[Content] = {
    var bound = Interval.singleton(time)
    var entry = intervalToSeg.rangeUntil(bound).lastOption
    while (entry.isDefined) {
      val current = entry.get
      val id = current._2
      if (!isBlock(id) && !skip.contains(id)) {
        segment(id) match {
          case x: Content => return Some(x)
          case _ =>
        }
      }
      bound = current._1
      entry = intervalToSeg.rangeUntil(bound).lastOption
    }
    None
  }

  /** 起点不小于 time 的第一个内容片段，跳过 skip 里的条目。 */
  private def contentAtOrAfter(time: Instant, skip: Set[Id]): Option[Content] = {
    intervalToSeg.rangeFrom(Interval.singleton(time)).iterator
      .map(_._2)
      .filter(id => !isBlock(id) && !skip.contains(id))
      .map(segment)
      .collectFirst { case x: Content => x }
  }

  private def syncAround(c: Content)(using ids: IdSource): Track = {
    val t = prevContent(c).map(syncTransition).getOrElse(this)
    t.syncTransition(c)
  }

  private def syncTransition(left: Content)(using ids: IdSource): Track = {
    val old: Option[Id] = transitions.get(left.id)
    nextContent(left) match {
      case None =>
        unplaceTransition(left)
      case Some(right) =>
        val lo = right.id.interval.lo
        val hi = left.id.interval.hi
        if (lo < hi) {
          old.filter(id => contains(id)) match {
            case Some(currentId) =>
              val r = currentId.interval
              if (r.lo == lo && r.hi == hi) this else put(segment(currentId), lo ~~ hi, lo)
            case None =>
              old match {
                case Some(trId) => put(segment(trId), lo ~~ hi, lo).indexTransition(left.id, trId)
                case None =>
                  val tr = newTransition(left, right)
                  put(tr, lo ~~ hi, lo).indexTransition(left.id, tr.id)
              }
          }
        } else if (old.isEmpty) {
          this
        } else {
          old.filter(id => contains(id)).map(dropFromLayout).getOrElse(this).unindexTransition(left.id)
        }
    }
  }

  private def unplaceTransition(c: Content): Track =
    transitions.get(c.id).filter(id => contains(id)).map(dropFromLayout).getOrElse(this)

  private def indexTransition(leftId: Id, trId: Id): Track =
    withTransitions(transitions.updated(leftId, trId))

  private def unindexTransition(leftId: Id): Track = {
    transitions.get(leftId) match {
      case Some(trId) =>
        val next = withTransitions(transitions.removed(leftId))
        if (next.contains(trId)) next else next.withSegments(next.segments.removed(trId))
      case None => this
    }
  }

  /** 移除片段。 */
  protected[timeline] def remove(segment: Segment)(using ids: IdSource): Track = remove(segment.id)

  protected[timeline] def remove(id: Id)(using ids: IdSource): Track = {
    if (!contains(id)) {
      this
    } else {
      segment(id) match {
        case t: Transition => removeTransition(t)
        case c: Content => removeContent(c)
      }
    }
  }

  private def removeContent(c: Content)(using ids: IdSource): Track = {
    val p = prevContent(c)
    var t = removeSegment(c.id).unplaceTransition(c)
    (p, nextContent(c)) match {
      case (Some(left), Some(right)) =>
        val pr = t.interval(left)
        val nr = t.interval(right)
        if (nr.lo < pr.hi && !(pr.lo < nr.lo && pr.hi < nr.hi)) {
          val target = nr.lo.max(pr.hi)
          if (target < nr.hi) {
            t = t.put(right, target ~~ nr.hi, t.getOrigin(right))
          }
        }
      case _ =>
    }
    p.map(t.syncTransition).getOrElse(t)
  }

  /** 删除转场，即消除两侧内容的重叠。 */
  private def removeTransition(t: Transition)(using ids: IdSource): Track = {
    val sides = transitionSides(t)
    if (sides.isEmpty) return this
    val (left, right) = sides.get
    val tr = t.interval
    val lr = left.interval
    val rr = right.interval
    val half = tr.len / 2
    val lower = (lr.lo + 1.micros).max(minStartOf(right)).max(endLower(left))
    var boundary = lr.hi - half
    boundary = boundary.max(lower)
    boundary = boundary.min(rr.hi - 1.micros)
    nextContent(right).foreach(rn => boundary = boundary.min(rn.interval.lo - 1.micros))
    if (boundary < lower || boundary <= lr.lo || boundary >= rr.hi) return this
    val next = dropFromLayout(t.id)
      .put(left, lr.lo ~~ boundary, getOrigin(left))
      .put(right, boundary ~~ rr.hi, getOrigin(right))
    next.syncAround(left)
  }

  /** 移除这批片段。 */
  private def removeAll(idsToRemove: Iterable[Id])(using ids: IdSource): Track = {
    var t = this
    for (id <- idsToRemove) {
      t = t.remove(id)
    }
    t
  }

  /** 在 time 处把内容片段一分为二。 */
  protected[timeline] def split(time: Instant): Track = ???

  /** 裁切一组片段的起始边缘（各自终点不变）。 */
  protected[timeline] def setStart(ids: Iterable[Id], deltaTime: Timespan)(using source: IdSource): Track = {
    var t = this
    for (id <- ids) {
      if (t.contains(id)) t = t.setStart(id, deltaTime)
    }
    t
  }

  protected[timeline] def setStart(id: Id, deltaTime: Timespan)(using ids: IdSource): Track = segment(id) match {
    case t: Transition => setTransitionStart(t, deltaTime)
    case c: Content => setContentStart(c, deltaTime)
  }

  /** 裁切一组片段的结束边缘（各自起点不变）。 */
  protected[timeline] def setEnd(ids: Iterable[Id], deltaTime: Timespan)(using source: IdSource): Track = {
    var t = this
    for (id <- ids) {
      if (t.contains(id)) t = t.setEnd(id, deltaTime)
    }
    t
  }

  protected[timeline] def setEnd(id: Id, deltaTime: Timespan)(using ids: IdSource): Track = segment(id) match {
    case t: Transition => setTransitionEnd(t, deltaTime)
    case c: Content => setContentEnd(c, deltaTime)
  }

  private def setContentStart(c: Content, deltaTime: Timespan)(using ids: IdSource): Track = {
    val r = c.id.interval
    val lower = startLower(c)
    val upper = startUpper(c)
    if (lower > upper) return this
    val target = (r.lo + deltaTime).max(lower).min(upper)
    if (target == r.lo) return this
    put(c, target ~~ r.hi, getOrigin(c)).syncAround(c)
  }

  private def setContentEnd(c: Content, deltaTime: Timespan)(using ids: IdSource): Track = {
    val r = c.id.interval
    val lower = endLower(c)
    val upper = endUpper(c)
    if (lower > upper) return this
    val target = (r.hi + deltaTime).max(lower).min(upper)
    if (target == r.hi) return this
    put(c, r.lo ~~ target, getOrigin(c)).syncAround(c)
  }

  private def setTransitionStart(t: Transition, deltaTime: Timespan)(using ids: IdSource): Track = transitionSides(t) match {
    case Some((_, right)) => setContentStart(right, deltaTime)
    case None => this
  }

  private def setTransitionEnd(t: Transition, deltaTime: Timespan)(using ids: IdSource): Track = transitionSides(t) match {
    case Some((left, _)) => setContentEnd(left, deltaTime)
    case None => this
  }

  private def startLower(c: Content): Instant = {
    val r = c.id.interval
    var lower = Instant.Zero.max(minStartOf(c))
    prevContent(c) match {
      case Some(p) =>
        val pr = p.id.interval
        val cap = if (canTransition(p, c) && r.hi > pr.hi) pr.lo + 1.micros else pr.hi
        lower = lower.max(cap)
        prevContent(p).foreach(pp => lower = lower.max(pp.id.interval.hi))
      case None =>
    }
    lower
  }

  private def startUpper(c: Content): Instant = {
    val r = c.id.interval
    var upper = r.hi - 1.micros
    nextContent(c).foreach { n =>
      val nr = n.id.interval
      if (nr.lo > r.lo && r.hi > nr.lo) {
        upper = upper.min(nr.lo - 1.micros)
      }
    }
    upper
  }

  private def endLower(c: Content): Instant = {
    val r = c.id.interval
    var lower = r.lo + 1.micros
    prevContent(c).foreach { p =>
      val pr = p.id.interval
      if (pr.hi > r.lo) {
        lower = lower.max(pr.hi + 1.micros)
      }
    }
    lower
  }

  private def endUpper(c: Content): Instant = {
    val r = c.id.interval
    var upper = maxEndOf(c)
    nextContent(c).foreach { n =>
      val nr = n.id.interval
      val cap = if (canTransition(c, n)) nr.hi - 1.micros else nr.lo
      upper = upper.min(cap)
      nextContent(n).foreach(nn => upper = upper.min(nn.id.interval.lo))
    }
    upper
  }

  private def canTransition(from: Content, to: Content): Boolean = {
    from.canCreateTransitionWith(to) || to.canCreateTransitionWith(from)
  }

  private def newTransition(from: Content, to: Content)(using ids: IdSource): Transition = {
    if (from.canCreateTransitionWith(to)) from.createTransition(to) else to.createTransition(from)
  }

  /** 整条转场刚性平移，转场宽度不变。 */
  protected[timeline] def shiftTransition(t: Transition, delta: Timespan)(using ids: IdSource): Track = {
    if (delta == Timespan.Zero || !contains(t)) return this
    val sides = transitionSides(t)
    if (sides.isEmpty) return this
    val (left, right) = sides.get
    val lr = left.id.interval
    val rr = right.id.interval
    val minShift = (minStartOf(right) - rr.lo)
      .max((lr.lo + 1.micros) - rr.lo)
      .max(endLower(left) - lr.hi)
      .max((lr.lo + 1.micros) - lr.hi)
    var maxShift = (maxEndOf(left) - lr.hi).min((rr.hi - lr.hi) - 1.micros)
    nextContent(right).foreach(rn => maxShift = maxShift.min((rn.id.interval.lo - 1.micros) - rr.lo))
    if (minShift > maxShift) return this
    val applied = delta.max(minShift).min(maxShift)
    if (applied == Timespan.Zero) return this
    put(left, lr.lo ~~ (lr.hi + applied), getOrigin(left))
      .put(right, (rr.lo + applied) ~~ rr.hi, getOrigin(right))
      .syncAround(left)
  }

  private def derived(layout: Track.Layout,
                      segToOrigin: Map[Id, Instant],
                      segments: Map[Id, Segment]): Track =
    new Track(index, segments, layout, segToOrigin, transitions)

  private def withTransitions(transitions: Track.TransitionIndex): Track =
    new Track(index, segments, layout, segToOrigin, transitions)

  private def withSegments(segments: Map[Id, Segment]): Track =
    new Track(index, segments, layout, segToOrigin, transitions)

  /** interval 内是否没有被 ignore 之外的片段占用。 */
  def isFree(interval: Interval, ignore: Set[Id]): Boolean = {
    intersectingEntries(intervalToSeg, interval).forall { case (_, id) => ignore.contains(id) }
  }

  protected[timeline] def startLimit(ids: Iterable[Id], forward: Boolean): Timespan = {
    val offsets = ids.iterator
      .filter(id => contains(id))
      .map { id =>
        segment(id) match {
          case t: Transition => startLimit(t, forward)
          case c: Content => startLimit(c, forward)
        }
      }
    if (forward) offsets.minOption.getOrElse(Timespan.MaxValue) else offsets.maxOption.getOrElse(Timespan.MinValue)
  }

  protected[timeline] def endLimit(ids: Iterable[Id], forward: Boolean): Timespan = {
    val offsets = ids.iterator
      .filter(id => contains(id))
      .map { id =>
        segment(id) match {
          case t: Transition => endLimit(t, forward)
          case c: Content => endLimit(c, forward)
        }
      }
    if (forward) offsets.minOption.getOrElse(Timespan.MaxValue) else offsets.maxOption.getOrElse(Timespan.MinValue)
  }

  private def startLimit(c: Content, forward: Boolean): Timespan = {
    val r = c.id.interval
    if (forward) (startUpper(c) - r.lo).max(Timespan.Zero) else (startLower(c) - r.lo).min(Timespan.Zero)
  }

  private def endLimit(c: Content, forward: Boolean): Timespan = {
    val r = c.id.interval
    if (forward) (endUpper(c) - r.hi).max(Timespan.Zero) else (endLower(c) - r.hi).min(Timespan.Zero)
  }

  private def startLimit(t: Transition, forward: Boolean): Timespan =
    transitionSides(t).map(s => startLimit(s._2, forward)).getOrElse(Timespan.Zero)

  private def endLimit(t: Transition, forward: Boolean): Timespan =
    transitionSides(t).map(s => endLimit(s._1, forward)).getOrElse(Timespan.Zero)

  /** 素材起点。 */
  private def minStartOf(segment: Segment): Instant = segment match {
    case _: Clip => Instant.Zero.max(getOrigin(segment))
    case _ => Instant.Zero
  }

  /** 素材终点。 */
  private def maxEndOf(segment: Segment): Instant = segment match {
    case c: Clip => getOrigin(c) + c.getDuration
    case _ => Instant.MaxValue
  }

  protected[timeline] def moveLimit(ids: Iterable[Id], forward: Boolean)(using source: IdSource): Timespan = {
    val noBlock: Timespan = if (forward) Timespan.MaxValue else Timespan.MinValue
    val moving: Set[Id] = ids.toSet
    lazy val bare: Track = removeAll(moving)
    val offsets = moving.iterator
      .filter(id => contains(id))
      .map { id =>
        segment(id) match {
          case t: Transition =>
            transitionSides(t) match {
              case Some((left, right)) if !moving.contains(left.id) && !moving.contains(right.id) =>
                bare.moveLimit(left.id, right.id, left.id.interval, right.id.interval,
                  maxEndOf(left), minStartOf(right), forward)
              case _ =>
                noBlock
            }
          case c: Content => moveLimit(c, c.id.interval, moving, forward)
        }
      }
    if (forward) offsets.minOption.getOrElse(noBlock) else offsets.maxOption.getOrElse(noBlock)
  }

  private def moveLimit(c: Content, r: Interval, moving: Set[Id], forward: Boolean): Timespan = {
    if (forward) {
      var bound = Timespan.MaxValue
      nextContent(c).foreach { n =>
        val nr = n.id.interval
        if (!moving.contains(n.id)) {
          bound = bound.min(
            if (canTransition(c, n)) (nr.lo - 1.micros - r.lo).min(nr.hi - 1.micros - r.hi) else nr.lo - r.hi)
        }
        nextContent(n).foreach(nn => if (!moving.contains(nn.id)) bound = bound.min(nn.id.interval.lo - r.hi))
      }
      bound.max(Timespan.Zero)
    } else {
      var bound = Instant.Zero - r.lo
      prevContent(c).foreach { p =>
        val pr = p.id.interval
        if (!moving.contains(p.id)) {
          val reach =
            if (canTransition(p, c)) (pr.hi - r.lo).min((pr.lo + 1.micros - r.lo).max(pr.hi + 1.micros - r.hi))
            else pr.hi - r.lo
          bound = bound.max(reach)
        }
        prevContent(p).foreach(pp => if (!moving.contains(pp.id)) bound = bound.max(pp.id.interval.hi - r.lo))
      }
      bound.min(Timespan.Zero)
    }
  }

  private def moveLimit(leftId: Id, rightId: Id, lr: Interval, rr: Interval,
                        hiCap: Instant, loCap: Instant, forward: Boolean): Timespan = {
    def skip(id: Id): Boolean = id == leftId || id == rightId
    if (forward) {
      var bound = hiCap - lr.hi
      val n = intervalToSeg.rangeFrom(Interval.singleton(rr.lo)).iterator.map(_._2)
        .filter(id => !isBlock(id) && !skip(id))
        .map(segment)
        .collectFirst { case x: Content => x }
      n.foreach(x => bound = bound.min(x.id.interval.lo - rr.hi))
      bound.max(Timespan.Zero)
    } else {
      var bound = (loCap - rr.lo).max((lr.lo + 1.micros) - lr.hi)
      val p = intervalToSeg.rangeUntil(Interval.singleton(lr.lo)).iterator.map(_._2)
        .filter(id => !isBlock(id) && !skip(id))
        .map(segment)
        .collect { case x: Content => x }
        .toSeq.lastOption
      p.foreach(x => bound = bound.max(x.id.interval.hi - lr.lo))
      bound.min(Timespan.Zero)
    }
  }

  /** 包含 time 的条目。 */
  def get(time: Instant): Element = segmentAt(time).map(segment).getOrElse(
    Gap(contentBefore(time, Set.empty).map(_.id), contentAtOrAfter(time, Set.empty).map(_.id)))

  /** time 所在条目（片段、转场或空隙）的区间。 */
  def intervalAt(time: Instant): Interval = get(time).interval

  /** time 是否落在某个内容片段的区间内部，即能否在此分割。 */
  def canSplit(time: Instant): Boolean = segmentAt(time) match {
    case Some(id) =>
      segment(id) match {
        case _: Content =>
          val r = layout.reverse(id)
          time > r.lo && time < r.hi
        case _ => false
      }
    case None => false
  }

  /** 起点不小于 time 的首个片段。 */
  private[timeline] def sourceAtOrAfter(time: Instant): Option[Segment] =
    intervalToSeg.rangeFrom(Interval.singleton(time)).iterator.map(_._2).nextOption().map(segment)

  /** 起点小于 time 的最后一个片段。 */
  private[timeline] def sourceBefore(time: Instant): Option[Segment] =
    lastBefore(intervalToSeg, time).map(_._2).map(segment)

  /** 与 interval 有公共点的用户片段，按区间升序。 */
  def getIntersecting(interval: Interval): Seq[Segment] = {
    intersectingEntries(intervalToSeg, interval).map(_._2).filter(id => !isBlock(id)).map(segment).toList
  }

  /** 生成片段在绝对时间 time 的帧 */
  def frameAt(segment: Segment, time: Instant): Frame = {
    val relative = time - getOrigin(segment)
    val f = EvalClock.withTime(time, relative) {
      segment.get(relative, this)
    }
    if (f == null) null else f.withTime(time)
  }

  /** 把片段同步到绝对时间 time。 */
  def syncAt(segment: Segment, time: Instant): Unit = {
    segment.sync(time - getOrigin(segment), this)
  }

  /** 轨道上的用户片段 id。 */
  override def iterator: Iterator[Id] = {
    intervalToSeg.valuesIterator.filterNot(id => isBlock(id))
  }

  /** 调试用摘要，按时间顺序列出片段。 */
  override def toString: String = {
    val parts = intervalToSeg.iterator.filterNot((_, id) => isBlock(id)).map { case (r, id) =>
      segment(id) match {
        case c: Content =>
          c.displayName + "=[" + r.lo + "," + Track.timeText(r.hi) + ") o=" + getOrigin(c) + " m=" + Track.timeText(maxEndOf(c))
        case t: Transition =>
          t.displayName + "=[" + r.lo + "," + Track.timeText(r.hi) + ")"
      }
    }
    parts.mkString(", ")
  }

  /** 包含 time 的片段。 */
  private def segmentAt(time: Instant): Option[Id] = {
    var best: Option[Id] = None
    var bestLen = Timespan.MaxValue
    var current = lastAtOrBefore(Interval.upTo(time))
    while (current.isDefined) {
      val (r, id) = current.get
      if (r.hi <= time) return best
      if (r.len < bestLen) {
        best = Some(id)
        bestLen = r.len
      }
      current = intervalToSeg.maxBefore(r)
    }
    best
  }

  /** 与 interval 有公共点的条目，按区间升序。 */
  private def intersectingEntries(bt: immutable.TreeMap[Interval, Id], interval: Interval): Iterator[(Interval, Id)] = {
    if (interval.isEmpty) {
      Iterator.empty
    } else {
      bt.rangeUntil(Interval.singleton(interval.hi)).iterator
        .dropWhile(_._1.hi <= interval.lo)
    }
  }

  private def lastBefore(bt: immutable.TreeMap[Interval, Id], time: Instant): Option[(Interval, Id)] =
    bt.maxBefore(Interval.singleton(time))

  /** 键不大于 bound 的最后一个条目。 */
  private def lastAtOrBefore(bound: Interval): Option[(Interval, Id)] =
    intervalToSeg.get(bound) match {
      case Some(id) => Some((bound, id))
      case None => intervalToSeg.maxBefore(bound)
    }
}

object Track {
  private type Layout = BiMap[Interval, Id, TreeMap[Interval, Id], Map[Id, Interval]]

  private type TransitionIndex = BiMap[Id, Id, Map[Id, Id], Map[Id, Id]]

  /** 新建空轨道。 */
  private[timeline] def apply(index: Int): Track = {
    val blockSegment: Segment = new Boundless(Id.noId(), new BlockSource)
    val layout = BiMap(TreeMap.empty[Interval, Id], Map.empty[Id, Interval])
      .updated(Instant.MinValue ~~ Instant.Zero, Id.noId())
    new Track(index, Map(Id.noId() -> blockSegment), layout,
      Map.empty[Id, Instant],
      BiMap(Map.empty[Id, Id], Map.empty[Id, Id]))
  }

  private def timeText(time: Instant): String = {
    if (time == Instant.MaxValue) "∞" else time.value.toString
  }

}
