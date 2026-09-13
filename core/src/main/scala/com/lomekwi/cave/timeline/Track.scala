package com.lomekwi.cave.timeline

import com.google.common.collect.{Range, RangeMap, TreeRangeMap}
import com.google.common.eventbus.Subscribe
import com.google.common.primitives.Longs
import com.lomekwi.cave.pipeline.{Frame, GapFrame}
import com.badlogic.gdx.Gdx
import com.lomekwi.cave.timeline.playback.{PlayStateChangedEvent, Playhead, RefreshRequestEvent, SeekEvent}

import com.lomekwi.cave.util.Ranges.shift

import java.io.{IOException, ObjectInputStream, ObjectOutputStream, Serializable}
import java.util.{ArrayList, Collection, Collections, Iterator, List, Objects, Set}
import java.util.concurrent.{Future, Phaser}
import java.util.concurrent.locks.LockSupport

import java.util.Map.Entry

import scala.jdk.CollectionConverters.*

@SerialVersionUID(1L)
class Track(@transient private var timeline: Timeline, final val index: Int) extends Serializable with java.lang.Iterable[Segment] {
  @transient private var sources: RangeMap[java.lang.Long, Segment] = TreeRangeMap.create()

  private var length: Long = 0L
  private var lengthChanged: Boolean = true
  private var serializationRanges: Array[Long] = null
  private var serializationSources: List[Segment] = null
  @transient private var worker: TrackWorker = null

  worker = new TrackWorker()

  private[timeline] def setTimeline(timeline: Timeline): Unit = {
    this.timeline = timeline
  }

  protected[timeline] def isEmpty(): Boolean = this.synchronized {
    sources.asMapOfRanges().isEmpty()
  }

  /**
   * 尝试在轨道中加入一个片段。仅当可加入时才会被真的加入。
   * @return 最大可用偏移量：0 表示目标区间空闲、已按原位加入；非 0 表示被占用、未加入，
   * 返回能放下该区间的最近偏移（调用方可把目标区间偏移这么多后再试）。此语义专用于放置/粘贴。
   */
  protected[timeline] def tryAdd(segment: Segment, r: Range[java.lang.Long]): Long = this.synchronized {
    var shift = getShift(r)
    if (shift == 0) {
      `override`(segment, r)
    }
    shift
  }
  protected[timeline] def getShift(r: Range[java.lang.Long]): Long = this.synchronized {
    getShift(r, null)
  }
  protected[timeline] def getShift(r: Range[java.lang.Long], exclude: Range[java.lang.Long]): Long = this.synchronized {
    pickShift(getShiftForward(r, exclude), getShiftBackward(r, exclude))
  }
  protected[timeline] def getShiftForward(r: Range[java.lang.Long], exclude: Range[java.lang.Long]): Long = this.synchronized {
    shiftScan(r, exclude, List.of[Segment](), true)
  }
  protected[timeline] def getShiftBackward(r: Range[java.lang.Long], exclude: Range[java.lang.Long]): Long = this.synchronized {
    shiftScan(r, exclude, List.of[Segment](), false)
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

  private def shiftScan(r: Range[java.lang.Long], exclude: Range[java.lang.Long], ignore: Collection[Segment], forward: Boolean): Long = this.synchronized {
    val lo: Long = r.lowerEndpoint()
    val hi: Long = r.upperEndpoint()
    var s: Long = 0
    var step = 0
    while (step < Track.MAX_SLIDE_STEPS) {
      if (isFree(shift(r, s), exclude, ignore)) {
        return s
      }
      if (forward) {
        var maxEnd = Long.MinValue
        var found = false
        for (e <- sources.subRangeMap(shift(r, s)).asMapOfRanges().entrySet().asScala) {
          if (!(exclude != null && e.getKey().isConnected(exclude)) && !ignore.contains(e.getValue())) {
            found = true
            maxEnd = Math.max(maxEnd, e.getKey().upperEndpoint())
          }
        }
        if (!found) return s
        val next = maxEnd - lo
        if (next <= s) return Long.MaxValue
        s = next
      } else {
        var minStart = Long.MaxValue
        var found = false
        for (e <- sources.subRangeMap(shift(r, s)).asMapOfRanges().entrySet().asScala) {
          if (!(exclude != null && e.getKey().isConnected(exclude)) && !ignore.contains(e.getValue())) {
            found = true
            minStart = Math.min(minStart, e.getKey().lowerEndpoint())
          }
        }
        if (!found) return s
        val next = minStart - hi
        if (next >= s) return s
        s = next
      }
      step += 1
    }
    s
  }

  private def isFree(range: Range[java.lang.Long], exclude: Range[java.lang.Long], ignore: Collection[Segment]): Boolean = this.synchronized {
    val it = sources.subRangeMap(range).asMapOfRanges().entrySet().iterator()
    while (it.hasNext) {
      val e = it.next()
      if (!(exclude != null && e.getKey().isConnected(exclude)) && !ignore.contains(e.getValue())) {
        return false
      }
    }
    true
  }

  /**
   *只是不检查。千万不要真的拿来覆盖。
   * @author shan_luan_
   */
  protected[timeline] def `override`(segment: Segment, r: Range[java.lang.Long]): Unit = this.synchronized {
    assert(isFree(r, Collections.singleton(segment)))
    sources.put(r, segment)
    segment.setTrack(this)
    segment.setRange(r)
    onChanged()
  }
  protected[timeline] def remove(segment: Segment): Boolean = this.synchronized {
    val r = segment.getRange()
    if (r != null) {
      sources.remove(r)
      true
    } else {
      false
    }
  }
  protected[timeline] def remove(segments: Collection[Segment]): Unit = this.synchronized {
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
  def isFree(range: Range[java.lang.Long], ignore: Collection[Segment]): Boolean = this.synchronized {
    val m = sources.subRangeMap(range).asMapOfRanges()
    if (m.isEmpty()) return true
    if (ignore.isEmpty()) return false
    val it = m.entrySet().iterator()
    while (it.hasNext) {
      val entry = it.next()
      if (!ignore.contains(entry.getValue())) return false
    }
    true
  }

  protected[timeline] def split(time: Long): Boolean = this.synchronized {
    val s = sources.get(time)
    if (s == null) return false
    val r = s.getRange()
    val lo: Long = r.lowerEndpoint()
    val hi: Long = r.upperEndpoint()
    if (time <= lo || time >= hi) return false
    val right = s.duplicate()
    sources.remove(r)
    `override`(s, Range.closedOpen(java.lang.Long.valueOf(lo), java.lang.Long.valueOf(time)))
    `override`(right, Range.closedOpen(java.lang.Long.valueOf(time), java.lang.Long.valueOf(hi)))
    true
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
  protected[timeline] def probeSetStart(segments: Collection[Segment], forward: Boolean): Long = this.synchronized {
    segments.stream()
      .filter((s: Segment) => this.eq(s.getTrack()))
      .mapToLong((s: Segment) => {
        val r = s.getRange()
        val lo: Long = r.lowerEndpoint()
        if (forward) {
          r.upperEndpoint() - 1 - lo
        } else {
          Math.min(Longs.max(0, s.prevRange().upperEndpoint(), s.getMinStart()) - lo, 0)
        }
      })
      .reduce(if (forward) Long.MaxValue else Long.MinValue, Track.tighter)
  }
  private def own(segments: Collection[Segment]): List[Segment] = {
    val out = new ArrayList[Segment](segments.size())
    for (s <- segments.asScala) {
      if (this.eq(s.getTrack())) out.add(s)
    }
    out
  }
  protected[timeline] def setStart(segments: Collection[Segment], deltaTime: Long): Unit = this.synchronized {
    segments.stream().filter((s: Segment) => this.eq(s.getTrack())).forEach((s: Segment) => setStart(s, deltaTime))
  }
  protected[timeline] def setStart(segment: Segment, deltaTime: Long): Unit = this.synchronized {
    remove(segment)
    `override`(segment, Range.closedOpen(java.lang.Long.valueOf(segment.getRange().lowerEndpoint() + deltaTime), segment.getRange().upperEndpoint()))
  }

  /**
   * 探测终点沿指定方向可移动多少。右移（伸尾）受后继起点与源长度 maxEnd 限制，越界时冻结；
   * 左移（裁尾）仅受自身长度限制。
   */
  protected[timeline] def probeSetEnd(segments: Collection[Segment], forward: Boolean): Long = this.synchronized {
    segments.stream()
      .filter((s: Segment) => this.eq(s.getTrack()))
      .mapToLong((s: Segment) => {
        val r = s.getRange()
        val hi: Long = r.upperEndpoint()
        if (forward) {
          Math.max(Math.min(s.nextRange().lowerEndpoint(), s.getMaxEnd()) - hi, 0)
        } else {
          r.lowerEndpoint() + 1 - hi
        }
      })
      .reduce(if (forward) Long.MaxValue else Long.MinValue, Track.tighter)
  }
  protected[timeline] def setEnd(segments: Collection[Segment], deltaTime: Long): Unit = this.synchronized {
    segments.stream().filter((s: Segment) => this.eq(s.getTrack())).forEach((s: Segment) => setEnd(s, deltaTime))
  }
  protected[timeline] def setEnd(segment: Segment, deltaTime: Long): Unit = this.synchronized {
    remove(segment)
    `override`(segment, Range.closedOpen(segment.getRange().lowerEndpoint(), java.lang.Long.valueOf(segment.getRange().upperEndpoint() + deltaTime)))
  }

  /**
   * 探测整组沿指定方向可平移多少：每个成员看 next()/prev() 一个候选，
   * 是拖拽成员则跳过（其自身会继续找），否则即最近障碍；取全体最严者。
   * 左移还受时间轴 0 限制。
   */
  protected[timeline] def probeMove(segments: Collection[Segment], forward: Boolean): Long = this.synchronized {
    val noBlock: Long = if (forward) Long.MaxValue else Long.MinValue // 该方向无障碍 = 无界
    segments.stream()
      .filter((s: Segment) => this.eq(s.getTrack()))
      .mapToLong((s: Segment) => {
        val r = s.getRange()
        if (forward) {
          val next = s.next()
          if (next == null || segments.contains(next)) noBlock
          else next.getRange().lowerEndpoint() - r.upperEndpoint()
        } else {
          val lo: Long = r.lowerEndpoint()
          val prev = s.prev()
          val obstacle: Long = if (prev == null || segments.contains(prev)) noBlock
            else prev.getRange().upperEndpoint() - lo
          Track.tighter(-lo, obstacle) // 0 边界：偏移 ≥ -lo
        }
      })
      .reduce(noBlock, Track.tighter)
  }

  protected[timeline] def move(segments: Collection[Segment], deltaTime: Long): Unit = this.synchronized {
    val owned = own(segments)
    remove(owned)
    for (s <- owned.asScala) {
      `override`(s, shift(s.getRange(), deltaTime))
      s.offsetOrigin(deltaTime)
    }
  }

  def get(time: Long): Segment = this.synchronized {
    sources.get(time)
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
        return null
      } else {
        return sources.get(time)
      }
    } else if (offset > 0) {
      val m = sources.subRangeMap(Range.atLeast(java.lang.Long.valueOf(time))).asMapOfRanges()
      val it = m.entrySet().iterator()
      while (it.hasNext) {
        val entry = it.next()
        if (!(excludeHit && entry.getKey().contains(time))) return entry.getValue()
      }
    } else {
      val m = sources.subRangeMap(Range.atMost(java.lang.Long.valueOf(time))).asDescendingMapOfRanges()
      val it = m.entrySet().iterator()
      while (it.hasNext) {
        val entry = it.next()
        if (!(excludeHit && entry.getKey().contains(time))) return entry.getValue()
      }
    }
    null
  }

  def getLength(): Long = this.synchronized {
    if (lengthChanged) {
      if (sources.asMapOfRanges().isEmpty()) {
        length = 0
      } else {
        length = sources.span().upperEndpoint()
      }
      lengthChanged = false
    }
    length
  }
  def getSubRangeMapAsEntrySet(range: Range[java.lang.Long]): Set[Entry[Range[java.lang.Long], Segment]] = this.synchronized {
    Collections.unmodifiableSet(sources.subRangeMap(range).asMapOfRanges().entrySet())
  }

  /**
   * 两条轨道相等，当且仅当轨道索引相同、片段区间集合逐项相同。
   * RangeMap 的 entrySet 迭代顺序总是按区间从小到大，因此两侧可以逐项对齐比较。
   * 片段本身不做身份比较，而是逐字段比较（源类型/时长、origin、区间），
   * 以便跨时间线（如序列化快照）的结构对比。
   */
  override def equals(o: Any): Boolean = {
    if (this.asInstanceOf[AnyRef] eq o.asInstanceOf[AnyRef]) return true
    o match {
      case other: Track =>
        if (index != other.index) return false
        val a = sources.asMapOfRanges().entrySet()
        val b = other.sources.asMapOfRanges().entrySet()
        if (a.size() != b.size()) return false
        val ia = a.iterator()
        val ib = b.iterator()
        while (ia.hasNext()) {
          val ea = ia.next()
          val eb = ib.next()
          if (!ea.getKey().equals(eb.getKey())) return false
          if (!Track.segmentEquals(ea.getValue(), eb.getValue())) return false
        }
        true
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

  private def writeObject(oos: ObjectOutputStream): Unit = {
    val ranges = sources.asMapOfRanges()
    serializationRanges = new Array[Long](ranges.size() * 2)
    serializationSources = new ArrayList[Segment](ranges.values())
    var i = 0
    for (r <- ranges.keySet().asScala) {
      serializationRanges(i) = r.lowerEndpoint()
      serializationRanges(i + 1) = r.upperEndpoint()
      i += 2
    }
    oos.defaultWriteObject()
    serializationRanges = null
    serializationSources = null
  }

  private def readObject(ois: ObjectInputStream): Unit = {
    ois.defaultReadObject()
    sources = TreeRangeMap.create()
    if (serializationRanges == null || serializationSources == null) {
      Gdx.app.error("Track", "Track 序列化数据为 null")
    } else {
      var i = 0
      while (i < serializationRanges.length) {
        val r: Range[java.lang.Long] = Range.closedOpen(java.lang.Long.valueOf(serializationRanges(i)), java.lang.Long.valueOf(serializationRanges(i + 1)))
        val s = serializationSources.get(i / 2)
        sources.put(r, s)
        s.setRange(r)
        s.setTrack(this)
        i += 2
      }
      serializationRanges = null
      serializationSources = null
    }
  }

  def getTimeline(): Timeline = {
    timeline
  }

  def getWorker(): TrackWorker = {
    if (worker == null) {
      worker = new TrackWorker()
    }
    worker
  }

  override def iterator(): Iterator[Segment] = {
    sources.asMapOfRanges().values().iterator()
  }

  class TrackWorker extends Runnable {
    private final val gapFrame: GapFrame = new GapFrame(Track.this)
    private var sinkPhaser: Phaser = null
    private var future: Future[?] = null
    @volatile private var workerThread: Thread = null
    @volatile private var updateNeeded: Boolean = false

    timeline.project.projEventBus.register(this)

    def getSinkPhaser(): Phaser = {
      sinkPhaser
    }

    def getFuture(): Future[?] = {
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
        while (!Thread.currentThread().isInterrupted()) {
          var t: Long = p.getTime()
          if (!p.isPlaying()) {
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
                parkTime = next.getRange().lowerEndpoint() - t
                parkTime *= 1000
                parkTime = Math.max(parkTime, 1)
              }
              Gdx.app.debug("Track" + index, "轨道线程等待: " + parkTime / 1e9 + "秒")
              LockSupport.parkNanos(parkTime)
            } else {
              val r = s.getRange()
              Gdx.app.debug("Track" + index, "找到片段: " + s)
              s.sync(t)
              val end: Long = r.upperEndpoint()
              while (t < end && !updateNeeded && !Thread.currentThread().isInterrupted()) {
                t = timeline.project.playhead.getTime()
                val frame = s.get(t)
                if (!updateNeeded && frame != null) {
                  timeline.project.projEventBus.post(frame)
                  val phase = sinkPhaser.arrive()
                  try {
                    sinkPhaser.awaitAdvanceInterruptibly(phase)
                  } catch {
                    case ie: InterruptedException =>
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
    if (a == Long.MaxValue || a == Long.MinValue) return b
    if (b == Long.MaxValue || b == Long.MinValue) return a
    if (Math.abs(b) < Math.abs(a)) b else a
  }

  private final val MAX_SLIDE_STEPS = 10000

  private def segmentEquals(a: Segment, b: Segment): Boolean = {
    if (a.eq(b)) return true
    val sa = a.getSource()
    val sb = b.getSource()
    sa.getClass() == sb.getClass()
      && sa.getDuration() == sb.getDuration()
      && a.getOrigin() == b.getOrigin()
      && Objects.equals(a.getRange(), b.getRange())
  }
}
