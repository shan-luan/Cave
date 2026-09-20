package com.lomekwi.cave.timeline

import com.google.common.eventbus.Subscribe
import com.google.common.primitives.Longs
import com.lomekwi.cave.pipeline.{Frame, GapFrame}
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

@SerialVersionUID(1L)
class Track(@transient private var timeline: Timeline, final val index: Int) extends Serializable with java.lang.Iterable[Segment] {
  private var sources: mutable.TreeMap[Interval, Segment] = mutable.TreeMap.empty

  private var length: Long = 0L
  private var lengthChanged: Boolean = true
  @transient private var worker: TrackWorker = uninitialized

  worker = new TrackWorker()

  private[timeline] def setTimeline(timeline: Timeline): Unit = {
    this.timeline = timeline
    rebindSegments()
  }

  /**
   * 反序列化后把片段与所属轨道、区间重新关联：
   * 片段自身的 track/range 是 @transient，区间只随本轨道的映射表被还原。
   */
  private def rebindSegments(): Unit = {
    for ((r, s) <- sources) {
      s.setRange(r)
      s.setTrack(this)
    }
  }

  protected[timeline] def isEmpty: Boolean = this.synchronized {
    sources.isEmpty
  }

  /**
   * 尝试在轨道中加入一个片段。仅当可加入时才会被真的加入。
   * @return 最大可用偏移量：0 表示目标区间空闲、已按原位加入；非 0 表示被占用、未加入，
   *         返回能放下该区间的最近偏移（调用方可把目标区间偏移这么多后再试）。此语义专用于放置/粘贴。
   */
  protected[timeline] def tryAdd(segment: Segment, r: Interval): Long = this.synchronized {
    val shift = getShift(r)
    if (shift == 0) {
      addOrThrow(segment, r)
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
    shiftScan(r, exclude, util.List.of[Segment](), true)
  }
  private def getShiftBackward(r: Interval, exclude: Interval): Long = this.synchronized {
    shiftScan(r, exclude, util.List.of[Segment](), false)
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
  private def entryAt(time: Long): Option[(Interval, Segment)] = {
    sources.rangeTo(Interval(time, Long.MaxValue)).lastOption.filter(_._1.contains(time))
  }

  /**
   * 与 range 有公共点的条目，按区间升序。轨道内区间互不重叠，因此按起点排序后这些条目是连续的一段。
   */
  private def intersectingEntries(range: Interval): scala.collection.Iterator[(Interval, Segment)] = {
    if (range.isEmpty) {
      scala.collection.Iterator.empty
    } else {
      sources.rangeUntil(Interval(range.hi, range.hi)).iterator.dropWhile(_._1.hi <= range.lo)
    }
  }

  /** 起点不小于 time 的首个条目。 */
  private def firstAtOrAfter(time: Long): (Interval, Segment) = {
    sources.iteratorFrom(Interval(time, time)).nextOption().orNull
  }

  /** 起点小于 time 的最后一个条目。 */
  private def lastBefore(time: Long): (Interval, Segment) = {
    sources.rangeUntil(Interval(time, time)).lastOption.orNull
  }

  private def shiftScan(r: Interval, exclude: Interval, ignore: util.Collection[Segment], forward: Boolean): Long = this.synchronized {
    val lo: Long = r.lo
    val hi: Long = r.hi

    @tailrec
    def scan(s: Long, step: Int): Long = {
      if (step >= Track.MAX_SLIDE_STEPS || isFree(r.shift(s), exclude, ignore)) {
        s
      } else {
        val obstacles = intersectingEntries(r.shift(s))
          .collect { case (interval, segment) if !ignorable(exclude, ignore, interval, segment) => interval }
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

  /** 该条目是否不构成障碍：与 exclude 相连，或在 ignore 中。 */
  private def ignorable(exclude: Interval, ignore: util.Collection[Segment], interval: Interval, segment: Segment): Boolean = {
    (exclude != null && interval.isConnected(exclude)) || ignore.contains(segment)
  }

  private def isFree(range: Interval, exclude: Interval, ignore: util.Collection[Segment]): Boolean = this.synchronized {
    intersectingEntries(range).forall { case (interval, segment) => ignorable(exclude, ignore, interval, segment) }
  }

  protected[timeline] def addOrThrow(segment: Segment, r: Interval): Unit = this.synchronized {
    require(isFree(r, Collections.singleton(segment)))
    sources.put(r, segment)
    segment.setTrack(this)
    segment.setRange(r)
    onChanged()
  }
  protected[timeline] def remove(segment: Segment): Boolean = this.synchronized {
    val r = segment.getRange
    if (r != null && sources.remove(r).isDefined) {
      onChanged()
      true
    } else {
      false
    }
  }
  protected[timeline] def remove(segments: util.Collection[Segment]): Unit = this.synchronized {
    for (s <- segments.asScala) {
      remove(s)
    }
  }

  /**
   * 检查指定时间范围是否空闲（忽略指定片段集合中的片段）
   *
   * @param range  要检查的时间范围
   * @param ignore 不视为障碍的片段集合（为空时相当于完全空闲检查）
   * @return 如果范围内没有任何非忽略片段占用则返回 true
   */
  def isFree(range: Interval, ignore: util.Collection[Segment]): Boolean = this.synchronized {
    intersectingEntries(range).forall { case (_, segment) => ignore.contains(segment) }
  }

  protected[timeline] def split(time: Long): Boolean = this.synchronized {
    val entry = entryAt(time)
    if (entry.isEmpty) {
      false
    } else {
      val s = entry.get._2
      val r = s.getRange
      val lo: Long = r.lo
      val hi: Long = r.hi
      if (time <= lo || time >= hi) {
        false
      } else {
        val right = s.duplicate()
        sources.remove(r)
        addOrThrow(s, Interval(lo, time))
        addOrThrow(right, Interval(time, hi))
        true
      }
    }
  }

  /* ------------------------------------------------------------------
   * 拖拽探测（probe*）与应用（setStart/setEnd/move）。
   *
   * 探测方法返回带符号的"最大可用偏移量"：沿 forward 指定方向最多可以
   * 移动多少并成功应用。forward=true 返回非负数（向时间增大方向），
   * forward=false 返回非正数（向时间减小方向），0 表示该方向上无法移动。
   * 调用方只需把请求的 deltaTime 同向截断到该偏移量（取绝对值较小者）
   * 即可直接应用，无需再按"修正量"换算或重试。方向上没有限制时用
   * Long.MaxValue / Long.MinValue 表示无界。
   * ------------------------------------------------------------------ */

  /**
   * 探测起点沿指定方向最多可移动多少。forward=右移（裁头，仅受自身长度限制）；
   * 左移（伸头）受 0、origin 与前邻限制，越界时冻结。
   */
  protected[timeline] def probeSetStart(segments: util.Collection[Segment], forward: Boolean): Long = this.synchronized {
    segments.stream()
      .filter((s: Segment) => this.eq(s.getTrack))
      .mapToLong((s: Segment) => {
        val r = s.getRange
        val lo: Long = r.lo
        if (forward) {
          r.hi - 1 - lo
        } else {
          Math.min(Longs.max(0, s.prevRange().fold(0L)(_.hi), s.getMinStart) - lo, 0)
        }
      })
      .reduce(if (forward) Long.MaxValue else Long.MinValue, Track.tighter)
  }
  private def own(segments: util.Collection[Segment]): util.List[Segment] = {
    val out = new util.ArrayList[Segment](segments.size())
    for (s <- segments.asScala) {
      if (this.eq(s.getTrack)) out.add(s)
    }
    out
  }
  protected[timeline] def setStart(segments: util.Collection[Segment], deltaTime: Long): Unit = this.synchronized {
    segments.stream().filter((s: Segment) => this.eq(s.getTrack)).forEach((s: Segment) => setStart(s, deltaTime))
  }
  protected[timeline] def setStart(segment: Segment, deltaTime: Long): Unit = this.synchronized {
    remove(segment)
    addOrThrow(segment, Interval(segment.getRange.lo + deltaTime, segment.getRange.hi))
  }

  /**
   * 探测终点沿指定方向可移动多少。右移（伸尾）受后继起点与源长度 maxEnd 限制，越界时冻结；
   * 左移（裁尾）仅受自身长度限制。
   */
  protected[timeline] def probeSetEnd(segments: util.Collection[Segment], forward: Boolean): Long = this.synchronized {
    segments.stream()
      .filter((s: Segment) => this.eq(s.getTrack))
      .mapToLong((s: Segment) => {
        val r = s.getRange
        val hi: Long = r.hi
        if (forward) {
          Math.max(Math.min(s.nextRange().fold(Long.MaxValue)(_.lo), s.getMaxEnd) - hi, 0)
        } else {
          r.lo + 1 - hi
        }
      })
      .reduce(if (forward) Long.MaxValue else Long.MinValue, Track.tighter)
  }
  protected[timeline] def setEnd(segments: util.Collection[Segment], deltaTime: Long): Unit = this.synchronized {
    segments.stream().filter((s: Segment) => this.eq(s.getTrack)).forEach((s: Segment) => setEnd(s, deltaTime))
  }
  protected[timeline] def setEnd(segment: Segment, deltaTime: Long): Unit = this.synchronized {
    remove(segment)
    addOrThrow(segment, Interval(segment.getRange.lo, segment.getRange.hi + deltaTime))
  }

  /**
   * 探测整组沿指定方向可平移多少：每个成员看 next()/prev() 一个候选，
   * 是拖拽成员则跳过（其自身会继续找），否则即最近障碍；取全体最严者。
   * 左移还受时间轴 0 限制。
   */
  protected[timeline] def probeMove(segments: util.Collection[Segment], forward: Boolean): Long = this.synchronized {
    val noBlock: Long = if (forward) Long.MaxValue else Long.MinValue // 该方向无障碍 = 无界
    segments.stream()
      .filter((s: Segment) => this.eq(s.getTrack))
      .mapToLong((s: Segment) => {
        val r = s.getRange
        if (forward) {
          val next = s.next()
          if (next == null || segments.contains(next)) noBlock
          else next.getRange.lo - r.hi
        } else {
          val lo: Long = r.lo
          val prev = s.prev()
          val obstacle: Long = if (prev == null || segments.contains(prev)) noBlock
            else prev.getRange.hi - lo
          Track.tighter(-lo, obstacle) // 0 边界：偏移 ≥ -lo
        }
      })
      .reduce(noBlock, Track.tighter)
  }

  protected[timeline] def move(segments: util.Collection[Segment], deltaTime: Long): Unit = this.synchronized {
    val owned = own(segments)
    remove(owned)
    for (s <- owned.asScala) {
      addOrThrow(s, s.getRange.shift(deltaTime))
      s.offsetOrigin(deltaTime)
    }
  }

  def get(time: Long): Segment = this.synchronized {
    val entry = entryAt(time)
    if (entry.isEmpty) null else entry.get._2
  }
  /**
   * 获取指定时间点的片段，支持偏移查找
   *
   * @param time      查询的时间点
   * @param offset    偏移量，0表示精确匹配时间点；正数表示查找该时间之后的第一个片段；负数表示查找该时间之前的最后一个片段。建议只使用-1,0,1，防止接口变动。
   * @param excludeHit 是否排除命中时间点的片段本身。true表示跳过包含time的片段，false表示可以返回包含time的片段
   * @return 找到的片段，如果未找到则返回null
   */
  def get(time: Long, offset: Int, excludeHit: Boolean): Segment = this.synchronized {
    if (offset == 0) {
      if (excludeHit) {
        null
      } else {
        val entry = entryAt(time)
        if (entry.isEmpty) null else entry.get._2
      }
    } else {
      val hit = entryAt(time)
      if (offset > 0) {
        if (hit.isDefined && !excludeHit) {
          hit.get._2
        } else {
          val from = if (hit.isDefined) hit.get._1.hi else time
          val entry = firstAtOrAfter(from)
          if (entry == null) null else entry._2
        }
      } else {
        if (hit.isDefined && hit.get._1.lo < time && !excludeHit) {
          hit.get._2
        } else {
          val from = if (hit.isDefined) hit.get._1.lo else time
          val entry = lastBefore(from)
          if (entry == null) null else entry._2
        }
      }
    }
  }

  def getLength: Long = this.synchronized {
    if (lengthChanged) {
      length = sources.lastOption.map(_._1.hi).getOrElse(0L)
      lengthChanged = false
    }
    length
  }
  /** 与 range 有公共点的片段，按区间升序；返回快照。 */
  def getIntersectingSegments(range: Interval): util.List[Segment] = this.synchronized {
    intersectingEntries(range).map(_._2).toList.asJava
  }

  /**
   * 两条轨道相等，当且仅当轨道索引相同、片段区间集合逐项相同。
   * 区间总是按起点从小到大迭代，因此两侧可以逐项对齐比较。
   * 片段本身不做身份比较，而是逐字段比较（源类型/时长、origin、区间），
   * 以便跨时间线（如序列化快照）的结构对比。
   */
  override def equals(o: Any): Boolean = {
    if (this.asInstanceOf[AnyRef] eq o.asInstanceOf[AnyRef]) {
      true
    } else o match {
      case other: Track =>
        if (index != other.index) {
          false
        } else {
          val a = sources.iterator.toIndexedSeq
          val b = other.sources.iterator.toIndexedSeq
          a.size == b.size && a.zip(b).forall { (ea, eb) =>
            ea._1.equals(eb._1) && Track.segmentEquals(ea._2, eb._2)
          }
        }
      case _ => false
    }
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

  override def iterator(): util.Iterator[Segment] = {
    sources.valuesIterator.asJava
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

            val s = get(t)
            var f: Frame = null
            if (s != null) {
              s.sync(t)
              f = s.get(t)
            }
            timeline.project.projEventBus.post(Objects.requireNonNullElse(f, gapFrame))

            LockSupport.park()
          } else {
            updateNeeded = false
            val s = get(t)
            if (s == null) {
              timeline.project.projEventBus.post(gapFrame)
              var parkTime: Long = Long.MaxValue
              val next = get(t, 1, false)
              if (next != null) {
                parkTime = next.getRange.lo - t
                parkTime *= 1000
                parkTime = Math.max(parkTime, 1)
              }
              Gdx.app.debug("Track" + index, "轨道线程等待: " + parkTime / 1e9 + "秒")
              LockSupport.parkNanos(parkTime)
            } else {
              val r = s.getRange
              Gdx.app.debug("Track" + index, "找到片段: " + s)
              s.sync(t)
              val end: Long = r.hi
              while (t < end && !updateNeeded && !Thread.currentThread().isInterrupted) {
                t = timeline.project.playhead.getTime
                val frame = s.get(t)
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

  private def segmentEquals(a: Segment, b: Segment): Boolean = {
    if (a.eq(b)) {
      true
    } else {
      val sa = a.getSource
      val sb = b.getSource
      sa.getClass == sb.getClass
        && sa.getDuration == sb.getDuration
        && a.getOrigin == b.getOrigin
        && Objects.equals(a.getRange, b.getRange)
    }
  }
}
