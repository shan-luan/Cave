package com.lomekwi.cave.timeline

import com.google.common.primitives.Longs
import com.lomekwi.cave.pipeline.{BlockSrc, Frame, Source}

import java.io.Serializable
import java.util
import java.util.Collections
import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.immutable.TreeMap
import scala.jdk.CollectionConverters.*

/**
 * 轨道。轨道被元素（{@link Segment} 与 {@link Gap}）完整划分：任意时刻恰好由一个元素占据。
 * 因为区间首尾相接，内容表只以区间起点为键，右端点取相邻条目的起点（末尾条目一直延伸到时间轴尽头）；
 * 源内偏移（origin）与元素到起点的反查各存一张表。
 *
 * 不可变：每次编辑都返回新实例，原实例保持不变。内容表是持久化结构，新旧版本共享绝大部分节点，
 * 因此编辑成本只与改动路径有关，与轨道长度无关；旧版本可以安全地留给撤销栈与序列化快照。
 * 当前版本由 {@link Timeline#setTrack} 发布，读取方永远从 {@link Timeline#getTrack} 取最新版本。
 */
@SerialVersionUID(1L)
final class Track private ( val timeline: Timeline,  val index: Int,
                           private val blockSource: BlockSrc,
                           private val byTime: TreeMap[Long, Element],
                           private val placements: Map[Element, Long],
                           private val origins: Map[Source[?], Long]) extends Serializable with java.lang.Iterable[Element] {

  /** 是否是占据 0 点左侧的阻挡片段。它只提供左边界，对遍历不可见。 */
  private def isBlock(element: Element): Boolean = element match {
    case Segment(source) => source.eq(blockSource)
    case _: Gap => false
  }

  /** 轨道是否没有用户内容。阻挡片段是地基，不算。 */
  protected[timeline] def isEmpty: Boolean = !byTime.valuesIterator.exists {
    case Segment(source) => !source.eq(blockSource)
    case _: Gap => false
  }

  /** 元素占用的区间；不在本轨道时返回 null。 */
  def getRange(element: Element): Interval = placements.get(element) match {
    case Some(lo) => Interval(lo, hiOf(byTime, lo))
    case None => null
  }

  /** 片段占用的区间；不在本轨道时返回 null。 */
  def getRange(source: Source[?]): Interval = getRange(Segment(source))

  /** 片段的 0 秒在时间轴中的位置；不在本轨道时返回 0。 */
  def getOrigin(source: Source[?]): Long = origins.getOrElse(source, 0L)

  def contains(element: Element): Boolean = placements.contains(element)

  def contains(source: Source[?]): Boolean = contains(Segment(source))

  /** 轨道长度：最后一个片段的终点；没有片段时为 0。 */
  lazy val length: Long = byTime.iterator.collect { case (lo, _: Segment) => hiOf(byTime, lo) }.maxOption.getOrElse(0L)

  def getLength: Long = length

  // 编辑。全部返回新实例，原实例不变。

  /**
   * 尝试在轨道中加入一个源。仅当可加入时才会被真的加入。
   * @return `(新轨道, 最大可用偏移量)`：偏移量为 0 表示目标区间空闲、已按原位加入，返回的轨道是加入后的版本；
   *         非 0 表示被占用、未加入，返回的是原轨道，且偏移量是能放下该区间的最近偏移（调用方可偏移这么多后再试）。
   *         此语义专用于放置/粘贴。
   */
  protected[timeline] def tryAdd(source: Source[?], r: Interval, origin: Long): (Track, Long) = {
    val shift = getShift(r)
    if (shift == 0) (addOrThrow(source, r, origin), 0L) else (this, shift)
  }

  /**
   * 把源放到指定区间。origin 是源的 0 秒在时间轴中的位置。
   * 要求区间空闲，因此落点所在的既有条目只可能是空隙：把该空隙按新片段切开。
   */
  protected[timeline] def addOrThrow(source: Source[?], r: Interval, origin: Long): Track = {
    require(isFree(r, Collections.singleton[Source[?]](source)))
    val segment = Segment(source)
    val (hostLo, host) = lastAtOrBefore(byTime, r.lo)
    val hostHi: Long = hiOf(byTime, hostLo)
    var bt = byTime.removed(hostLo)
    var pl = placements.removed(host)
    if (hostLo < r.lo) {
      val left = new Gap
      bt = bt.updated(hostLo, left)
      pl = pl.updated(left, hostLo)
    }
    if (r.hi < hostHi) {
      val right = new Gap
      bt = bt.updated(r.hi, right)
      pl = pl.updated(right, r.hi)
    }
    derived(bt.updated(r.lo, segment), pl.updated(segment, r.lo), origins.updated(source, origin))
  }

  /** 移除源。源不在本轨道时原样返回本实例。 */
  protected[timeline] def remove(source: Source[?]): Track = {
    val r = getRange(source)
    if (r == null) {
      this
    } else {
      val gap = new Gap
      derived(byTime.updated(r.lo, gap), placements.updated(gap, r.lo).removed(Segment(source)), origins.removed(source))
        .relayout(r.lo, r.hi)
    }
  }

  /** 移除这批源，返回新版本。不在本轨道的源会被忽略。 */
  protected[timeline] def removeAll(sources: util.Collection[Source[?]]): Track = {
    var t = this
    for (s <- sources.asScala) {
      t = t.remove(s)
    }
    t
  }

  /** 在 time 处把片段一分为二。time 不落在片段内部时原样返回本实例。 */
  protected[timeline] def split(time: Long): Track = entryAt(byTime, time) match {
    case Some((r, Segment(source))) if time > r.lo && time < r.hi =>
      val origin: Long = getOrigin(source)
      val right = source.duplicate()
      // 两半共用同一个 origin：源内时间 = 绝对时间 - origin，右半才能接着左半的内容播
      remove(source)
        .addOrThrow(source, Interval(r.lo, time), origin)
        .addOrThrow(right, Interval(time, r.hi), origin)
    case _ => this
  }

  /** 裁切一组源的起始边缘（各自终点不变）。 */
  protected[timeline] def setStart(sources: util.Collection[Source[?]], deltaTime: Long): Track = {
    var t = this
    for (s <- sources.asScala) {
      if (t.contains(s)) t = t.setStart(s, deltaTime)
    }
    t
  }

  protected[timeline] def setStart(source: Source[?], deltaTime: Long): Track = {
    val r = getRange(source)
    val origin = getOrigin(source)
    remove(source).addOrThrow(source, Interval(r.lo + deltaTime, r.hi), origin)
  }

  /** 裁切一组源的结束边缘（各自起点不变）。 */
  protected[timeline] def setEnd(sources: util.Collection[Source[?]], deltaTime: Long): Track = {
    var t = this
    for (s <- sources.asScala) {
      if (t.contains(s)) t = t.setEnd(s, deltaTime)
    }
    t
  }

  protected[timeline] def setEnd(source: Source[?], deltaTime: Long): Track = {
    val r = getRange(source)
    val origin = getOrigin(source)
    remove(source).addOrThrow(source, Interval(r.lo, r.hi + deltaTime), origin)
  }

  /**
   * 重建 [lo, hi) 及其紧邻区域内的空隙，使内容表恢复完整划分。
   * 范围向外扩到左右两侧紧邻的片段：删掉元素后留下的空隙才能与相邻空隙合并成一个。
   * 要求 lo、hi 是元素区间的端点。幂等，重复调用结果不变。
   */
  private def relayout(lo: Long, hi: Long): Track = {
    val from: Long = lastBefore(byTime, lo) match {
      case null => -Long.MaxValue
      case (k, _: Segment) => if (lo < hiOf(byTime, k)) k else hiOf(byTime, k)
      case (k, _) => k
    }
    val to: Long = firstAtOrAfter(byTime, hi) match {
      case null => Long.MaxValue
      case (k, _: Segment) => k
      case (k, _) => hiOf(byTime, k)
    }
    if (from >= to) return this
    val region = byTime.rangeFrom(from).iterator.takeWhile(_._1 < to).toList
    var bt = byTime
    var pl = placements
    for (case (k, gap: Gap) <- region) {
      bt = bt.removed(k)
      pl = pl.removed(gap)
    }
    // 保留区间内的片段，用空隙补满它们之间与两端剩下的空间。片段的右端点取自原表，不受上面的删除影响
    var cursor: Long = from
    for (case (k, _: Segment) <- region) {
      if (cursor < k) {
        val gap = new Gap
        bt = bt.updated(cursor, gap)
        pl = pl.updated(gap, cursor)
      }
      cursor = hiOf(byTime, k)
    }
    if (cursor < to) {
      val gap = new Gap
      bt = bt.updated(cursor, gap)
      pl = pl.updated(gap, cursor)
    }
    derived(bt, pl, origins)
  }

  private def derived(byTime: immutable.TreeMap[Long, Element],
                      placements: Map[Element, Long],
                      origins: Map[Source[?], Long]): Track =
    new Track(timeline, index, blockSource, byTime, placements, origins)

  // 探测

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
          .collect { case (interval, _: Segment) => interval }
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

  /** 区间内没有片段。空隙从不构成障碍。 */
  private def noSegment(range: Interval): Boolean =
    intersectingEntries(byTime, range).forall { case (_, _: Gap) => true; case _ => false }

  /**
   * 检查指定时间范围是否空闲（忽略指定源集合中的源）
   *
   * @param range  要检查的时间范围
   * @param ignore 不视为障碍的源集合（为空时相当于完全空闲检查）
   * @return 如果范围内没有任何非忽略源占用则返回 true
   */
  def isFree(range: Interval, ignore: util.Collection[Source[?]]): Boolean = {
    intersectingEntries(byTime, range).forall {
      case (_, Segment(source)) => ignore.contains(source)
      case (_, _: Gap) => true
    }
  }

  /**
   * 探测起点沿指定方向最多可移动多少。forward=右移（裁头，仅受自身长度限制）；
   * 左移（伸头）受 0、origin 与前邻限制，越界时冻结。
   */
  protected[timeline] def probeSetStart(sources: util.Collection[Source[?]], forward: Boolean): Long = {
    sources.stream()
      .filter((s: Source[?]) => contains(s))
      .mapToLong((s: Source[?]) => {
        val r = getRange(s)
        val lo: Long = r.lo
        if (forward) {
          r.hi - 1 - lo
        } else {
          Math.min(Longs.max(prevRangeOf(s).fold(0L)(_.hi), minStartOf(s)) - lo, 0)
        }
      })
      .reduce(if (forward) Long.MaxValue else Long.MinValue, Track.tighter)
  }

  /** 拉伸头时允许的最小起点：源的 0 秒不能越过时间轴 0 点。 */
  private def minStartOf(source: Source[?]): Long = {
    Math.max(0, getOrigin(source))
  }

  /** 拉伸尾时允许的最大终点。 */
  private def maxEndOf(source: Source[?]): Long = {
    val duration = source.getDuration
    if (duration == Long.MaxValue) Long.MaxValue else getOrigin(source) + duration
  }

  /**
   * 探测终点沿指定方向可移动多少。右移（伸尾）受后继起点与源长度 maxEnd 限制，越界时冻结；
   * 左移（裁尾）仅受自身长度限制。
   */
  protected[timeline] def probeSetEnd(sources: util.Collection[Source[?]], forward: Boolean): Long = {
    sources.stream()
      .filter((s: Source[?]) => contains(s))
      .mapToLong((s: Source[?]) => {
        val r = getRange(s)
        val hi: Long = r.hi
        if (forward) {
          Math.max(Math.min(nextRangeOf(s).fold(Long.MaxValue)(_.lo), maxEndOf(s)) - hi, 0)
        } else {
          r.lo + 1 - hi
        }
      })
      .reduce(if (forward) Long.MaxValue else Long.MinValue, Track.tighter)
  }

  /**
   * 探测整组沿指定方向可平移多少：在摘掉整组之后的布局上看每个成员的最近障碍，取全体最严者。
   * 整组刚性平移，成员之间不会互相成为障碍，因此直接在不含本组的版本上量即可。
   * 左移还受时间轴 0 限制（由 0 点左侧的阻挡片段表达）。
   */
  protected[timeline] def probeMove(sources: util.Collection[Source[?]], forward: Boolean): Long = {
    val noBlock: Long = if (forward) Long.MaxValue else Long.MinValue // 该方向无障碍 = 无界
    val bare = removeAll(sources)
    sources.stream()
      .filter((s: Source[?]) => contains(s))
      .mapToLong((s: Source[?]) => {
        val r = getRange(s)
        if (forward) {
          val next = bare.sourceAtOrAfter(r.hi)
          if (next == null) noBlock else bare.getRange(next).lo - r.hi
        } else {
          val prev = bare.sourceBefore(r.lo)
          if (prev == null) noBlock else bare.getRange(prev).hi - r.lo
        }
      })
      .reduce(noBlock, Track.tighter)
  }

  // 查询

  /** 包含 time 的元素；轨道之外为空。 */
  def get(time: Long): Element = entryAt(byTime, time).map(_._2).orNull

  /** time 是否落在某个片段内部，即能否在此分割。 */
  def canSplit(time: Long): Boolean = entryAt(byTime, time) match {
    case Some((r, _: Segment)) => time > r.lo && time < r.hi
    case _ => false
  }

  /** 同轨道上紧随其后的片段；没有时为空。 */
  def nextOf(source: Source[?]): Source[?] = {
    val r = getRange(source)
    if (r == null) null else sourceAtOrAfter(r.hi)
  }

  /** 同轨道上紧邻其前的片段；没有时为空。 */
  def prevOf(source: Source[?]): Source[?] = {
    val r = getRange(source)
    if (r == null) null else sourceBefore(r.lo)
  }

  def nextRangeOf(source: Source[?]): Option[Interval] = {
    val next = nextOf(source)
    if (next != null) Some(getRange(next)) else None
  }

  def prevRangeOf(source: Source[?]): Option[Interval] = {
    val prev = prevOf(source)
    if (prev != null) Some(getRange(prev)) else None
  }

  /** 起点不小于 time 的首个片段，取其源；没有时返回 null。 */
  private[timeline] def sourceAtOrAfter(time: Long): Source[?] = {
    byTime.rangeFrom(time).valuesIterator.collectFirst { case Segment(s) => s }.orNull
  }

  /** 起点小于 time 的最后一个片段，取其源；没有时返回 null。 */
  private[timeline] def sourceBefore(time: Long): Source[?] = {
    // 空隙互不相邻，故最多退两步就能越过它
    @tailrec
    def scan(t: Long): Source[?] = lastBefore(byTime, t) match {
      case null => null
      case (_, Segment(s)) => s
      case (lo, _: Gap) => scan(lo)
    }
    scan(time)
  }

  /** 与 range 有公共点的用户条目，按区间升序；返回快照。 */
  def getIntersecting(range: Interval): util.List[Element] = {
    intersectingEntries(byTime, range).map(_._2).filterNot(isBlock).toList.asJava
  }

  /** 生成源在绝对时间 time 的帧。 */
  def frameAt(source: Source[?], time: Long): Frame = {
    val f = source.get(time - getOrigin(source), this)
    if (f == null) null else f.withTime(time)
  }

  /** 把源同步到绝对时间 time。 */
  def syncAt(source: Source[?], time: Long): Unit = {
    source.sync(time - getOrigin(source), this)
  }

  /** 轨迹线程：按轨道索引唯一，由 {@link Timeline} 持有，故轨道换版本时它保持不变。 */
  def getWorker: timeline.TrackWorker = timeline.getWorker(index)

  def getTimeline: Timeline = timeline

  /** 轨道上的用户条目；阻挡片段对遍历不可见。 */
  override def iterator(): util.Iterator[Element] = {
    byTime.valuesIterator.filterNot(isBlock).asJava
  }

  /**
   * 两条轨道相等，当且仅当轨道索引相同、条目逐项相同。
   * 条目总是按起点从小到大迭代，因此两侧可以逐项对齐比较；起点两两相同也就意味着区间两两相同。
   */
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
   * 两个条目相等：片段逐字段比源（类型/时长）连同 origin，空隙只比类型。
   * 起点已经作为键比过了。逐字段而非按身份，是为了跨时间线（如序列化快照）的结构对比。
   */
  private def entryEquals(a: Element, b: Element, other: Track): Boolean = (a, b) match {
    case (Segment(sa), Segment(sb)) => Track.sourceEquals(sa, sb) && getOrigin(sa) == other.getOrigin(sb)
    case (_: Gap, _: Gap) => true
    case _ => false
  }

  override def hashCode(): Int = {
    Integer.hashCode(index)
  }

  // 索引查询。都以内容表为参数。

  /** 包含 time 的条目；轨道之外为空。 */
  private def entryAt(bt: immutable.TreeMap[Long, Element], time: Long): Option[(Interval, Element)] = {
    bt.rangeTo(time).lastOption
      .filter { case (lo, _) => time < hiOf(bt, lo) }
      .map { case (lo, element) => (Interval(lo, hiOf(bt, lo)), element) }
  }

  /**
   * 与 range 有公共点的条目，按区间升序。轨道内区间互不重叠，因此按起点排序后这些条目是连续的一段。
   */
  private def intersectingEntries(bt: immutable.TreeMap[Long, Element], range: Interval): Iterator[(Interval, Element)] = {
    if (range.isEmpty) {
      Iterator.empty
    } else {
      bt.rangeUntil(range.hi).iterator
        .map { case (lo, element) => (Interval(lo, hiOf(bt, lo)), element) }
        .dropWhile(_._1.hi <= range.lo)
    }
  }

  /** 起点不小于 time 的首个条目；没有时返回 null。 */
  private def firstAtOrAfter(bt: immutable.TreeMap[Long, Element], time: Long): (Long, Element) = {
    bt.rangeFrom(time).iterator.nextOption().orNull
  }

  /** 起点小于 time 的最后一个条目；没有时返回 null。 */
  private def lastBefore(bt: immutable.TreeMap[Long, Element], time: Long): (Long, Element) = {
    bt.rangeUntil(time).lastOption.orNull
  }

  /** 起点不大于 time 的最后一个条目。时间轴被完整划分，因此对轨道内的时刻总是存在。 */
  private def lastAtOrBefore(bt: immutable.TreeMap[Long, Element], time: Long): (Long, Element) = {
    bt.rangeTo(time).lastOption.orNull
  }

  /** 以 lo 为起点的条目的右端点：下一个条目的起点；已是最后一个条目时延伸到时间轴尽头。 */
  private def hiOf(bt: immutable.TreeMap[Long, Element], lo: Long): Long = {
    bt.rangeFrom(lo).iterator.drop(1).nextOption().map(_._1).getOrElse(Long.MaxValue)
  }
}

object Track {
  /**
   * 新建空轨道：0 点左侧是阻挡片段（地基），0 点右侧是无界空隙，
   * 因此时间轴被元素完整划分，拖拽与裁切不必再单独判断左边界。
   */
  private[timeline] def apply(timeline: Timeline, index: Int): Track = {
    val blockSource = new BlockSrc
    val block = Segment(blockSource)
    val tail = new Gap
    val byTime: immutable.TreeMap[Long, Element] = immutable.TreeMap[Long, Element](-Long.MaxValue -> block, 0L -> tail)
    new Track(timeline, index, blockSource, byTime,
      Map[Element, Long](block -> -Long.MaxValue, tail -> 0L), Map[Source[?], Long](blockSource -> 0L))
  }

  /** 返回离 0 更近的偏移量（限制更严者）；MAX_VALUE/MIN_VALUE 视为"无界"参与合并。 */
  private[timeline] def tighter(a: Long, b: Long): Long = {
    if (a == Long.MaxValue || a == Long.MinValue) b
    else if (b == Long.MaxValue || b == Long.MinValue) a
    else if (Math.abs(b) < Math.abs(a)) b
    else a
  }

  private final val MAX_SLIDE_STEPS = 10000

  private def sourceEquals(a: Source[?], b: Source[?]): Boolean = {
    if (a.eq(b)) {
      true
    } else {
      a.getClass == b.getClass && a.getDuration == b.getDuration
    }
  }
}
