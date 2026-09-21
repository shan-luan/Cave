package com.lomekwi.cave.timeline

import com.google.common.eventbus.Subscribe
import com.google.common.primitives.Longs
import com.lomekwi.cave.pipeline.{BlockSrc, Frame, GapFrame, Source}
import com.badlogic.gdx.Gdx
import com.lomekwi.cave.timeline.playback.{PlayStateChangedEvent, RefreshRequestEvent, SeekEvent}

import java.io.Serializable
import java.util
import java.util.{Collections, Objects}
import java.util.concurrent.{Future, Phaser}
import java.util.concurrent.locks.LockSupport

import scala.annotation.tailrec
import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

/**
 * 轨道。轨道被元素（{@link Segment} 与 {@link Gap}）完整划分：任意时刻恰好由一个元素占据。
 * 区间是时间索引的键，源内偏移（origin）存在条目表里。
 */
@SerialVersionUID(1L)
class Track(@transient private var timeline: Timeline, final val index: Int) extends Serializable with java.lang.Iterable[Element] {
  /** 时间索引：区间 → 元素。键按起点有序且首尾相接，完整覆盖 [0, Long.MaxValue)。 */
  private val byTime: mutable.TreeMap[Interval, Element] = mutable.TreeMap.empty
  /** 元素 → 区间。正向（区间 → 元素）是 byTime，这张是反查。写时复制，读不加锁，供播放线程与 UI 同时访问。 */
  @volatile private var placements: Map[Element, Interval] = Map.empty
  /** 片段的源内偏移。空隙没有这个量，所以不并进 placements。 */
  @volatile private var origins: Map[Source[?], Long] = Map.empty

  private var length: Long = 0L
  private var lengthChanged: Boolean = true
  @transient private var worker: TrackWorker = uninitialized
  /** 占据 0 点左侧的阻挡源。它是轨道的地基，不算用户内容。 */
  private final val blockSource: BlockSrc = new BlockSrc

  worker = new TrackWorker()
  // 时间轴被元素完整划分：0 点左侧是阻挡片段，0 点右侧是无界空隙
  {
    byTime.put(Interval(-Long.MaxValue, 0L), Segment(blockSource))
    byTime.put(Interval(0L, Long.MaxValue), new Gap)
    placements = byTime.iterator.map { case (r, element) => element -> r }.toMap
    origins = Map(blockSource -> 0L)
  }

  private[timeline] def setTimeline(timeline: Timeline): Unit = {
    this.timeline = timeline
  }

  /** 是否是占据 0 点左侧的阻挡片段。它只提供左边界，对遍历不可见。 */
  private def isBlock(element: Element): Boolean = element match {
    case Segment(source) => source.eq(blockSource)
    case _: Gap => false
  }

  /** 轨道是否没有用户内容。阻挡片段是地基，不算。 */
  protected[timeline] def isEmpty: Boolean = this.synchronized {
    !byTime.valuesIterator.exists {
      case Segment(source) => !source.eq(blockSource)
      case _: Gap => false
    }
  }

  /** 元素占用的区间；不在本轨道时返回 null。 */
  def getRange(element: Element): Interval = placements.getOrElse(element, null)

  /** 片段占用的区间；不在本轨道时返回 null。 */
  def getRange(source: Source[?]): Interval = getRange(Segment(source))

  /** 片段的 0 秒在时间轴中的位置；不在本轨道时返回 0。 */
  def getOrigin(source: Source[?]): Long = origins.getOrElse(source, 0L)

  def contains(element: Element): Boolean = placements.contains(element)

  def contains(source: Source[?]): Boolean = contains(Segment(source))

  /**
   * 尝试在轨道中加入一个源。仅当可加入时才会被真的加入。
   * @return 最大可用偏移量：0 表示目标区间空闲、已按原位加入；非 0 表示被占用、未加入，
   *         返回能放下该区间的最近偏移（调用方可把目标区间偏移这么多后再试）。此语义专用于放置/粘贴。
   */
  protected[timeline] def tryAdd(source: Source[?], r: Interval, origin: Long): Long = this.synchronized {
    val shift = getShift(r)
    if (shift == 0) {
      addOrThrow(source, r, origin)
    }
    shift
  }
  private def getShift(r: Interval): Long = this.synchronized {
    getShift(r, null)
  }
  private def getShift(r: Interval, exclude: Interval): Long = this.synchronized {
    pickShift(getShiftForward(r, exclude), getShiftBackward(r, exclude))
  }
  private def getShiftForward(r: Interval, exclude: Interval): Long = this.synchronized {
    shiftScan(r, exclude, util.List.of[Source[?]](), true)
  }
  private def getShiftBackward(r: Interval, exclude: Interval): Long = this.synchronized {
    shiftScan(r, exclude, util.List.of[Source[?]](), false)
  }

  private def pickShift(forward: Long, backward: Long): Long = this.synchronized {
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

  /** 包含 time 的条目；没有时为空。 */
  private def entryAt(time: Long): Option[(Interval, Element)] = {
    byTime.rangeTo(Interval(time, Long.MaxValue)).lastOption.filter(_._1.contains(time))
  }

  /**
   * 与 range 有公共点的条目，按区间升序。轨道内区间互不重叠，因此按起点排序后这些条目是连续的一段。
   */
  private def intersectingEntries(range: Interval): scala.collection.Iterator[(Interval, Element)] = {
    if (range.isEmpty) {
      scala.collection.Iterator.empty
    } else {
      byTime.rangeUntil(Interval(range.hi, range.hi)).iterator.dropWhile(_._1.hi <= range.lo)
    }
  }

  /** 起点不小于 time 的首个条目。 */
  private def firstAtOrAfter(time: Long): (Interval, Element) = {
    byTime.iteratorFrom(Interval(time, time)).nextOption().orNull
  }

  /** 起点小于 time 的最后一个条目。 */
  private def lastBefore(time: Long): (Interval, Element) = {
    byTime.rangeUntil(Interval(time, time)).lastOption.orNull
  }

  private def shiftScan(r: Interval, exclude: Interval, ignore: util.Collection[Source[?]], forward: Boolean): Long = this.synchronized {
    val lo: Long = r.lo
    val hi: Long = r.hi

    @tailrec
    def scan(s: Long, step: Int): Long = {
      if (step >= Track.MAX_SLIDE_STEPS || isFree(r.shift(s), exclude, ignore)) {
        s
      } else {
        val obstacles = intersectingEntries(r.shift(s))
          .collect { case (interval, element) if !ignorable(exclude, ignore, interval, element) => interval }
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

  /** 该条目是否不构成障碍：空隙从不阻挡，片段则看是否与 exclude 相连或在 ignore 中。 */
  private def ignorable(exclude: Interval, ignore: util.Collection[Source[?]], interval: Interval, element: Element): Boolean = element match {
    case Segment(source) => (exclude != null && interval.isConnected(exclude)) || ignore.contains(source)
    case _: Gap => true
  }

  private def isFree(range: Interval, exclude: Interval, ignore: util.Collection[Source[?]]): Boolean = this.synchronized {
    intersectingEntries(range).forall { case (interval, element) => ignorable(exclude, ignore, interval, element) }
  }

  /**
   * 重建 [lo, hi) 及其紧邻区域内的空隙，使 byTime 恢复完整划分。
   * 范围向外扩到左右两侧紧邻的片段：删除元素后两侧的空隙才能合并成一个。
   * 要求 lo、hi 是元素区间的端点。幂等，重复调用结果不变。
   */
  private def relayout(lo: Long, hi: Long): Unit = this.synchronized {
    val from: Long = lastBefore(lo) match {
      case null => -Long.MaxValue
      case (r, _: Segment) => if (r.contains(lo)) r.lo else r.hi
      case (r, _) => r.lo
    }
    val to: Long = firstAtOrAfter(hi) match {
      case null => Long.MaxValue
      case (r, _: Segment) => r.lo
      case (r, _) => r.hi
    }
    if (from >= to) return
    val rng = Interval(from, to)
    var table = placements
    for ((gapRange, gap) <- intersectingEntries(rng).collect { case (range, gap: Gap) => (range, gap) }.toList) {
      byTime.remove(gapRange)
      table = table.removed(gap)
    }
    val segments = intersectingEntries(rng).collect { case (segRange, _: Segment) => segRange }.toList
    var cursor: Long = from
    for (r <- segments) {
      if (cursor < r.lo) {
        val gap = new Gap
        val gapRange = Interval(cursor, r.lo)
        byTime.put(gapRange, gap)
        table = table.updated(gap, gapRange)
      }
      cursor = r.hi
    }
    if (cursor < to) {
      val gap = new Gap
      val gapRange = Interval(cursor, to)
      byTime.put(gapRange, gap)
      table = table.updated(gap, gapRange)
    }
    placements = table
  }

  /** 把源放到指定区间。origin 是源的 0 秒在时间轴中的位置。 */
  protected[timeline] def addOrThrow(source: Source[?], r: Interval, origin: Long): Unit = this.synchronized {
    require(isFree(r, Collections.singleton[Source[?]](source)))
    val segment = Segment(source)
    byTime.put(r, segment)
    placements = placements.updated(segment, r)
    origins = origins.updated(source, origin)
    relayout(r.lo, r.hi)
    onChanged()
  }

  protected[timeline] def remove(source: Source[?]): Boolean = this.synchronized {
    val r = getRange(source)
    if (r == null) {
      false
    } else {
      byTime.remove(r)
      placements = placements.removed(Segment(source))
      origins = origins.removed(source)
      relayout(r.lo, r.hi)
      onChanged()
      true
    }
  }

  /**
   * 检查指定时间范围是否空闲（忽略指定源集合中的源）
   *
   * @param range  要检查的时间范围
   * @param ignore 不视为障碍的源集合（为空时相当于完全空闲检查）
   * @return 如果范围内没有任何非忽略源占用则返回 true
   */
  def isFree(range: Interval, ignore: util.Collection[Source[?]]): Boolean = this.synchronized {
    intersectingEntries(range).forall {
      case (_, Segment(source)) => ignore.contains(source)
      case (_, _: Gap) => true
    }
  }

  protected[timeline] def split(time: Long): Boolean = this.synchronized {
    entryAt(time) match {
      case Some((r, Segment(source))) =>
        val lo: Long = r.lo
        val hi: Long = r.hi
        if (time <= lo || time >= hi) {
          false
        } else {
          val origin: Long = getOrigin(source)
          val right = source.duplicate()
          byTime.remove(r)
          placements = placements.removed(Segment(source))
          // 两半共用同一个 origin：源内时间 = 绝对时间 - origin，右半才能接着左半的内容播
          addOrThrow(source, Interval(lo, time), origin)
          addOrThrow(right, Interval(time, hi), origin)
          true
        }
      case _ => false
    }
  }

  /**
   * 探测方法返回带符号的"最大可用偏移量"：沿 forward 方向最多可移动并成功应用的距离。
   * forward=false 时返回非正数，0 表示该方向无法移动；无限制时用 Long.MaxValue/MinValue 表示无界。
   */

  /**
   * 探测起点沿指定方向最多可移动多少。forward=右移（裁头，仅受自身长度限制）；
   * 左移（伸头）受 0、origin 与前邻限制，越界时冻结。
   */
  protected[timeline] def probeSetStart(sources: util.Collection[Source[?]], forward: Boolean): Long = this.synchronized {
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

  protected[timeline] def setStart(sources: util.Collection[Source[?]], deltaTime: Long): Unit = this.synchronized {
    sources.stream().filter((s: Source[?]) => contains(s)).forEach((s: Source[?]) => setStart(s, deltaTime))
  }
  protected[timeline] def setStart(source: Source[?], deltaTime: Long): Unit = this.synchronized {
    val r = getRange(source)
    val origin = getOrigin(source)
    byTime.remove(r)
    placements = placements.removed(Segment(source))
    addOrThrow(source, Interval(r.lo + deltaTime, r.hi), origin)
  }

  /**
   * 探测终点沿指定方向可移动多少。右移（伸尾）受后继起点与源长度 maxEnd 限制，越界时冻结；
   * 左移（裁尾）仅受自身长度限制。
   */
  protected[timeline] def probeSetEnd(sources: util.Collection[Source[?]], forward: Boolean): Long = this.synchronized {
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
  protected[timeline] def setEnd(sources: util.Collection[Source[?]], deltaTime: Long): Unit = this.synchronized {
    sources.stream().filter((s: Source[?]) => contains(s)).forEach((s: Source[?]) => setEnd(s, deltaTime))
  }
  protected[timeline] def setEnd(source: Source[?], deltaTime: Long): Unit = this.synchronized {
    val r = getRange(source)
    val origin = getOrigin(source)
    byTime.remove(r)
    placements = placements.removed(Segment(source))
    addOrThrow(source, Interval(r.lo, r.hi + deltaTime), origin)
  }

  /**
   * 探测整组沿指定方向可平移多少：每个成员看后一个/前一个候选，
   * 是拖拽成员则跳过（其自身会继续找），否则即最近障碍；取全体最严者。
   * 左移还受时间轴 0 限制。
   */
  protected[timeline] def probeMove(sources: util.Collection[Source[?]], forward: Boolean): Long = this.synchronized {
    val noBlock: Long = if (forward) Long.MaxValue else Long.MinValue // 该方向无障碍 = 无界
    sources.stream()
      .filter((s: Source[?]) => contains(s))
      .mapToLong((s: Source[?]) => {
        val r = getRange(s)
        if (forward) {
          val next = nextOf(s)
          if (next == null || sources.contains(next)) noBlock
          else getRange(next).lo - r.hi
        } else {
          val prev = prevOf(s)
          if (prev == null || sources.contains(prev)) noBlock
          else getRange(prev).hi - r.lo
        }
      })
      .reduce(noBlock, Track.tighter)
  }

  /** 同轨道上紧随其后的片段；没有时为空。 */
  def nextOf(source: Source[?]): Source[?] = this.synchronized {
    val r = getRange(source)
    if (r == null) null
    else intersectingEntries(Interval(r.hi, Long.MaxValue)).collectFirst { case (_, Segment(s)) => s }.orNull
  }

  /** 同轨道上紧邻其前的片段；没有时为空。 */
  def prevOf(source: Source[?]): Source[?] = this.synchronized {
    val r = getRange(source)
    if (r == null) null
    else {
      // 空隙互不相邻，故最多前进两步就能越过它
      @tailrec
      def scan(time: Long): Source[?] = lastBefore(time) match {
        case null => null
        case (_, Segment(s)) => s
        case (gapRange, _: Gap) => scan(gapRange.lo)
      }
      scan(r.lo)
    }
  }

  def nextRangeOf(source: Source[?]): Option[Interval] = {
    val next = nextOf(source)
    if (next != null) Some(getRange(next)) else None
  }

  def prevRangeOf(source: Source[?]): Option[Interval] = {
    val prev = prevOf(source)
    if (prev != null) Some(getRange(prev)) else None
  }

  /** 包含 time 的元素；轨道之外为空。 */
  def get(time: Long): Element = this.synchronized {
    entryAt(time).map(_._2).orNull
  }

  /** 轨道长度：最后一个片段的终点；没有片段时为 0。 */
  def getLength: Long = this.synchronized {
    if (lengthChanged) {
      length = byTime.iterator.collect { case (r, _: Segment) => r.hi }.maxOption.getOrElse(0L)
      lengthChanged = false
    }
    length
  }

  /** 起点不小于 time 的首个片段，取其源；没有时返回 null。 */
  private[timeline] def sourceAtOrAfter(time: Long): Source[?] = this.synchronized {
    byTime.iteratorFrom(Interval(time, time)).collectFirst { case (_, Segment(s)) => s }.orNull
  }

  /** 与 range 有公共点的用户条目，按区间升序；返回快照。 */
  def getIntersecting(range: Interval): util.List[Element] = this.synchronized {
    intersectingEntries(range).map(_._2).filterNot(isBlock).toList.asJava
  }

  /**
   * 两条轨道相等，当且仅当轨道索引相同、条目逐项相同。
   * 区间总是按起点从小到大迭代，因此两侧可以逐项对齐比较。
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
            ia.equals(ib) && entryEquals(ea, eb, other)
          }
        }
      case _ => false
    }
  }

  /**
   * 两个条目相等：片段逐字段比源（类型/时长）连同 origin，空隙只比类型。
   * 区间已经作为键比过了。逐字段而非按身份，是为了跨时间线（如序列化快照）的结构对比。
   */
  private def entryEquals(a: Element, b: Element, other: Track): Boolean = (a, b) match {
    case (Segment(sa), Segment(sb)) => Track.sourceEquals(sa, sb) && getOrigin(sa) == other.getOrigin(sb)
    case (_: Gap, _: Gap) => true
    case _ => false
  }

  override def hashCode(): Int = {
    Integer.hashCode(index)
  }

  private def onChanged(): Unit = {
    lengthChanged = true
    if (worker != null) {
      worker.onTrackChanged()
    }
  }

  def getTimeline: Timeline = {
    timeline
  }

  def getWorker: TrackWorker = {
    if (worker == null) {
      worker = new TrackWorker()
    }
    worker
  }

  /** 轨道上的用户条目；阻挡片段对遍历不可见。 */
  override def iterator(): util.Iterator[Element] = {
    byTime.valuesIterator.filterNot(isBlock).asJava
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

  class TrackWorker extends Runnable {
    private final val gapFrame: GapFrame = new GapFrame(Track.this)
    private var sinkPhaser: Phaser = uninitialized
    private var future: Future[?] = uninitialized
    @volatile private var workerThread: Thread = uninitialized
    @volatile private var updateNeeded: Boolean = false

    timeline.project.projEventBus.register(this)

    def getSinkPhaser: Phaser = {
      sinkPhaser
    }

    def getFuture: Future[?] = {
      future
    }

    def setFuture(future: Future[?]): Unit = {
      this.future = future
    }

    override def run(): Unit = {
      workerThread = Thread.currentThread()
      sinkPhaser = new Phaser(1)
      Gdx.app.log("Track" + index, "轨道线程启动: " + Track.this)
      try {
        val p = timeline.project.playhead
        while (!Thread.currentThread().isInterrupted) {
          var t: Long = p.getTime
          if (!p.isPlaying) {
            Gdx.app.debug("Track" + index, "因为播放头而尝试park...")

            var f: Frame = null
            get(t) match {
              case Segment(source) =>
                syncAt(source, t)
                f = frameAt(source, t)
              case _: Gap | null =>
            }
            timeline.project.projEventBus.post(Objects.requireNonNullElse(f, gapFrame))

            LockSupport.park()
          } else {
            updateNeeded = false
            get(t) match {
              case Segment(source) =>
                val r = getRange(source)
                Gdx.app.debug("Track" + index, "找到源: " + source)
                syncAt(source, t)
                val end: Long = r.hi
                while (t < end && !updateNeeded && !Thread.currentThread().isInterrupted) {
                  t = timeline.project.playhead.getTime
                  val frame = frameAt(source, t)
                  if (!updateNeeded && frame != null) {
                    timeline.project.projEventBus.post(frame)
                    val phase = sinkPhaser.arrive()
                    try {
                      sinkPhaser.awaitAdvanceInterruptibly(phase)
                    } catch {
                      case _: InterruptedException =>
                        Thread.currentThread().interrupt()
                    }
                  }
                }
              case _: Gap | null =>
                timeline.project.projEventBus.post(gapFrame)
                var parkTime: Long = Long.MaxValue
                val next = sourceAtOrAfter(t)
                if (next != null) {
                  parkTime = getRange(next).lo - t
                  parkTime *= 1000
                  parkTime = Math.max(parkTime, 1)
                }
                Gdx.app.debug("Track" + index, "轨道线程等待: " + parkTime / 1e9 + "秒")
                LockSupport.parkNanos(parkTime)
            }
          }
        }
      } catch {
        case e: Exception =>
          if (!e.isInstanceOf[InterruptedException]) {
            Gdx.app.error("Track" + index, "在更新轨道时发生错误", e)
            Gdx.app.postRunnable(() => {
              throw new RuntimeException(e)
            })
          }
      } finally {
        workerThread = null
        Gdx.app.log("Track" + index, "轨道线程结束: " + Track.this)
      }
    }
    @Subscribe
    def onPlayStateChanged(event: PlayStateChangedEvent): Unit = {
      update()
    }
    @Subscribe
    def onRefreshRequested(event: RefreshRequestEvent): Unit = {
      update()
    }
    @Subscribe
    def onSeek(event: SeekEvent): Unit = {
      update()
    }
    protected[timeline] def onTrackChanged(): Unit = {
      update()
    }
    private def update(): Unit = {
      val t = workerThread
      if (t != null) {
        LockSupport.unpark(t)
      }
      updateNeeded = true
    }
  }
}

object Track {
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
