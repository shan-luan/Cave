package com.lomekwi.cave.timeline

import com.google.common.primitives.Longs
import com.lomekwi.cave.pipeline.{BlockSource, Content, Element, Frame, Gap, Segment}

import java.io.Serializable
import java.util
import java.util.Collections
import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.immutable.TreeMap
import scala.jdk.CollectionConverters.*

/**
 * 轨道。轨道只存片段（[[Segment]]），每条记录带自己的区间与片段内偏移（origin），
 * 片段之外的时间都是空隙。空隙不存储，[[get]] 与 [[rangeAt]] 查到空隙时
 * 临时物化一个 [[Gap]] 返回给调用方。
 *
 * 不可变。每次编辑都返回新实例。
 */
@SerialVersionUID(1L)
final class Track private (val timeline: Timeline, val index: Int,
                           private val blockSegment: Segment[Frame],
                           private val byTime: TreeMap[Interval, Segment[?]],
                           private val placements: Map[Segment[?], Interval],
                           private val origins: Map[Segment[?], Long]) extends Serializable with java.lang.Iterable[Segment[?]] {

  /** 是否是占据 0 点左侧的阻挡片段。它只提供左边界，对遍历不可见。 */
  private def isBlock(segment: Segment[?]): Boolean = segment.eq(blockSegment)

  /** 轨道是否没有用户内容。阻挡片段是地基，不算。 */
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

  /**
   * 尝试在轨道中加入一个片段。仅当可加入时才会被真的加入。
   * @return `(新轨道, 最大可用偏移量)`，偏移量为 0 表示目标区间空闲、已按原位加入，返回的轨道是加入后的版本；
   *         非 0 表示被占用、未加入，返回的是原轨道，且偏移量是能放下该区间的最近偏移（调用方可偏移这么多后再试）。
   *         此语义专用于放置/粘贴。
   */
  protected[timeline] def tryAdd(segment: Segment[?], r: Interval, origin: Long): (Track, Long) = {
    val shift = getShift(r)
    if (shift == 0) (addOrThrow(segment, r, origin), 0L) else (this, shift)
  }

  /**
   * 把片段放到指定区间。origin 是片段的 0 秒在时间轴中的位置。
   * 要求区间内没有别的片段。
   */
  protected[timeline] def addOrThrow(segment: Segment[?], r: Interval, origin: Long): Track = {
    require(isFree(r, Collections.singleton[Segment[?]](segment)))
    derived(byTime.updated(r, segment), placements.updated(segment, r), origins.updated(segment, origin))
  }

  /** 移除片段。片段不在本轨道时原样返回本实例。 */
  protected[timeline] def remove(segment: Segment[?]): Track = {
    if (!contains(segment)) {
      this
    } else {
      val r = placements(segment)
      derived(byTime.removed(r), placements.removed(segment), origins.removed(segment))
    }
  }

  /** 移除这批片段，返回新版本。不在本轨道的片段会被忽略。 */
  protected[timeline] def removeAll(segments: util.Collection[Segment[?]]): Track = {
    var t = this
    for (s <- segments.asScala) {
      t = t.remove(s)
    }
    t
  }

  /** 在 time 处把片段一分为二。time 不落在片段的区间内部时原样返回本实例。 */
  protected[timeline] def split(time: Long): Track = {
    val s = segmentAt(time)
    if (s == null) return this
    val r = placements(s)
    if (time <= r.lo || time >= r.hi) return this
    val origin: Long = getOrigin(s)
    val right = s.duplicate()
    // 两半共用同一个 origin，片段内时间 = 绝对时间 - origin，右半才能接着左半的内容播
    remove(s)
      .addOrThrow(s, r.lo ~~ time, origin)
      .addOrThrow(right, time ~~ r.hi, origin)
  }

  /** 裁切一组片段的起始边缘（各自终点不变）。 */
  protected[timeline] def setStart(segments: util.Collection[Segment[?]], deltaTime: Long): Track = {
    var t = this
    for (s <- segments.asScala) {
      if (t.contains(s)) t = t.setStart(s, deltaTime)
    }
    t
  }

  protected[timeline] def setStart(segment: Segment[?], deltaTime: Long): Track = {
    val r = getRange(segment)
    val origin = getOrigin(segment)
    remove(segment).addOrThrow(segment, (r.lo + deltaTime) ~~ r.hi, origin)
  }

  /** 裁切一组片段的结束边缘（各自起点不变）。 */
  protected[timeline] def setEnd(segments: util.Collection[Segment[?]], deltaTime: Long): Track = {
    var t = this
    for (s <- segments.asScala) {
      if (t.contains(s)) t = t.setEnd(s, deltaTime)
    }
    t
  }

  protected[timeline] def setEnd(segment: Segment[?], deltaTime: Long): Track = {
    val r = getRange(segment)
    val origin = getOrigin(segment)
    remove(segment).addOrThrow(segment, r.lo ~~ (r.hi + deltaTime), origin)
  }

  private def derived(byTime: immutable.TreeMap[Interval, Segment[?]],
                      placements: Map[Segment[?], Interval],
                      origins: Map[Segment[?], Long]): Track =
    new Track(timeline, index, blockSegment, byTime, placements, origins)

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

  /** 区间内没有片段。 */
  private def noSegment(range: Interval): Boolean =
    !intersectingEntries(byTime, range).hasNext

  /**
   * 检查指定时间范围是否空闲（忽略指定片段集合中的片段）
   *
   * @param range  要检查的时间范围
   * @param ignore 不视为障碍的片段集合（为空时相当于完全空闲检查）
   * @return 如果范围内没有任何非忽略片段占用则返回 true
   */
  def isFree(range: Interval, ignore: util.Collection[Segment[?]]): Boolean = {
    intersectingEntries(byTime, range).forall { case (_, s) => ignore.contains(s) }
  }

  /**
   * 探测起点沿指定方向最多可移动多少。forward=右移（裁头，仅受自身长度限制）；
   * 左移（伸头）受 0、origin 与前邻限制，越界时冻结。
   */
  protected[timeline] def probeSetStart(segments: util.Collection[Segment[?]], forward: Boolean): Long = {
    val offsets = segments.asScala.iterator
      .filter((s: Segment[?]) => contains(s))
      .map { s =>
        val r = getRange(s)
        if (forward) {
          r.hi - 1 - r.lo
        } else {
          Math.min(Longs.max(prevRangeOf(s).fold(0L)(_.hi), minStartOf(s)) - r.lo, 0)
        }
      }
    // 偏移与方向同号（正向非负、反向非正），正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(Long.MaxValue) else offsets.maxOption.getOrElse(Long.MinValue)
  }

  /** 拉伸头时允许的最小起点，片段的 0 秒不能越过时间轴 0 点。 */
  private def minStartOf(segment: Segment[?]): Long = {
    Math.max(0, getOrigin(segment))
  }

  private def maxEndOf(segment: Segment[?]): Long = {
    val duration = segment.getDuration
    if (duration == Long.MaxValue) Long.MaxValue else getOrigin(segment) + duration
  }

  /**
   * 探测终点沿指定方向可移动多少。右移（伸尾）受后继起点与片段长度 maxEnd 限制，越界时冻结；
   * 左移（裁尾）仅受自身长度限制。
   */
  protected[timeline] def probeSetEnd(segments: util.Collection[Segment[?]], forward: Boolean): Long = {
    val offsets = segments.asScala.iterator
      .filter((s: Segment[?]) => contains(s))
      .map { s =>
        val r = getRange(s)
        if (forward) {
          Math.max(Math.min(nextRangeOf(s).fold(Long.MaxValue)(_.lo), maxEndOf(s)) - r.hi, 0)
        } else {
          r.lo + 1 - r.hi
        }
      }
    // 偏移与方向同号（正向非负、反向非正），正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(Long.MaxValue) else offsets.maxOption.getOrElse(Long.MinValue)
  }

  /**
   * 探测整组沿指定方向可平移多少，在摘掉整组之后的布局上看每个成员的最近障碍，取全体最严者。
   * 整组刚性平移，成员之间不会互相成为障碍，因此直接在不含本组的版本上量即可。
   * 左移还受时间轴 0 限制（由 0 点左侧的阻挡片段表达）。
   */
  protected[timeline] def probeMove(segments: util.Collection[Segment[?]], forward: Boolean): Long = {
    val noBlock: Long = if (forward) Long.MaxValue else Long.MinValue // 该方向无障碍 = 无界
    val bare = removeAll(segments)
    val offsets = segments.asScala.iterator
      .filter((s: Segment[?]) => contains(s))
      .map { s =>
        val r = getRange(s)
        if (forward) {
          val next = bare.sourceAtOrAfter(r.hi)
          if (next == null) noBlock else bare.getRange(next).lo - r.hi
        } else {
          val prev = bare.sourceBefore(r.lo)
          if (prev == null) noBlock else bare.getRange(prev).hi - r.lo
        }
      }
    // 偏移与方向同号（正向非负、反向非正），正向取最小、反向取最大即最严限制
    if (forward) offsets.minOption.getOrElse(noBlock) else offsets.maxOption.getOrElse(noBlock)
  }

  /** 包含 time 的条目；落在空隙中时返回临时物化的 [[Gap]]。 */
  def get(time: Long): Element = {
    val s = segmentAt(time)
    if (s == null) new Gap else s
  }

  /** time 所在条目（片段或空隙）的区间。 */
  def rangeAt(time: Long): Interval = {
    val (r, _) = lastAtOrBefore(byTime, time)
    if (time < r.hi) {
      r
    } else {
      r.hi ~~ nextStart(r.hi)
    }
  }

  /** time 是否落在某个片段的区间内部，即能否在此分割。 */
  def canSplit(time: Long): Boolean = {
    val s = segmentAt(time)
    if (s == null) {
      false
    } else {
      val r = placements(s)
      time > r.lo && time < r.hi
    }
  }

  /** 同轨道上紧随其后的片段；没有时为空。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def nextOf(segment: Segment[?]): Segment[?] = sourceAtOrAfter(getRange(segment).hi)

  /** 同轨道上紧邻其前的片段；没有时为空。要求片段在本轨道，否则抛 IllegalArgumentException。 */
  def prevOf(segment: Segment[?]): Segment[?] = sourceBefore(getRange(segment).lo)

  def nextRangeOf(segment: Segment[?]): Option[Interval] = {
    val next = nextOf(segment)
    if (next != null) Some(getRange(next)) else None
  }

  def prevRangeOf(segment: Segment[?]): Option[Interval] = {
    val prev = prevOf(segment)
    if (prev != null) Some(getRange(prev)) else None
  }

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

  /**
   * 两条轨道相等，当且仅当轨道索引相同、条目逐项相同。
   * 条目总是按起点从小到大迭代，因此两侧可以逐项对齐比较。
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
   * 两个片段条目相等，逐字段比类型/时长，连同 origin；区间已经作为键比过了。
   * 逐字段而非按身份，是为了跨时间线（如序列化快照）的结构对比。
   */
  private def entryEquals(a: Segment[?], b: Segment[?], other: Track): Boolean = {
    Track.sourceEquals(a, b) && getOrigin(a) == other.getOrigin(b)
  }

  override def hashCode(): Int = {
    Integer.hashCode(index)
  }

  /** 包含 time 的片段；落在空隙中时返回 null。 */
  private def segmentAt(time: Long): Segment[?] = {
    val (r, s) = lastAtOrBefore(byTime, time)
    if (time < r.hi) s else null
  }

  /**
   * 与 range 有公共点的条目，按区间升序。轨道内片段互不重叠，因此按起点排序后这些条目是连续的一段。
   */
  private def intersectingEntries(bt: immutable.TreeMap[Interval, Segment[?]], range: Interval): Iterator[(Interval, Segment[?])] = {
    if (range.isEmpty) {
      Iterator.empty
    } else {
      bt.rangeUntil(at(range.hi)).iterator
        .dropWhile(_._1.hi <= range.lo)
    }
  }

  /** 圈住一切起点不大于 time 的条目，作 rangeTo 的上界。 */
  private def upTo(time: Long): Interval = time ~~ Long.MaxValue

  /** time 处的空区间，作起点与 time 的分界。 */
  private def at(time: Long): Interval = time ~~ time

  /** 起点小于 time 的最后一个条目；没有时返回 null。 */
  private def lastBefore(bt: immutable.TreeMap[Interval, Segment[?]], time: Long): (Interval, Segment[?]) = {
    bt.rangeUntil(at(time)).lastOption.orNull
  }

  /** 起点不大于 time 的最后一个条目。0 点左侧有阻挡片段，因此对轨道内的时刻总是存在。 */
  private def lastAtOrBefore(bt: immutable.TreeMap[Interval, Segment[?]], time: Long): (Interval, Segment[?]) = {
    bt.rangeTo(upTo(time)).lastOption.orNull
  }

  /** 起点不小于 time 的首个片段的起点；没有时是时间轴尽头。 */
  private def nextStart(time: Long): Long = {
    byTime.rangeFrom(at(time)).iterator.nextOption().map(_._1.lo).getOrElse(Long.MaxValue)
  }
}

object Track {
  /** 新建空轨道，0 点左侧是阻挡片段（地基），覆盖 [Long.MinValue, 0)，右侧全是空隙。 */
  private[timeline] def apply(timeline: Timeline, index: Int): Track = {
    val blockSegment: Segment[Frame] = new Content[Frame](new BlockSource)
    new Track(timeline, index, blockSegment,
      immutable.TreeMap[Interval, Segment[?]](Long.MinValue ~~ 0L -> blockSegment),
      Map[Segment[?], Interval](blockSegment -> (Long.MinValue ~~ 0L)),
      Map[Segment[?], Long](blockSegment -> 0L))
  }

  private final val MAX_SLIDE_STEPS = 10000

  private def sourceEquals(a: Segment[?], b: Segment[?]): Boolean = {
    if (a.eq(b)) {
      true
    } else {
      a.getClass == b.getClass && a.getDuration == b.getDuration
    }
  }
}
