package com.lomekwi.cave.timeline

import com.google.common.collect.Range
import com.lomekwi.cave.app.copy.Copyable
import com.lomekwi.cave.app.selection.Selectable
import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor
import com.lomekwi.cave.util.Duplicatable

import java.io.{ObjectInputStream, Serializable}
import java.util.Iterator

@SerialVersionUID(1L)
class Segment(private val source: Source[?]) extends Serializable with java.lang.Iterable[Frame] with Duplicatable[Segment] with Selectable with Copyable with Comparable[Segment] {
  protected[timeline] def sourceAccessor(): Source[?] = {
    source
  }
  def getSource(): Source[?] = {
    source
  }
  @transient private var track: Track = null
  @transient private var actor: SegActor = null
  @transient private var range: Range[java.lang.Long] = null
  @transient private var selected: Boolean = false
  private var group: SegmentGroup = null
  def isSelected(): Boolean = {
    selected
  }
  def setSelected(selected: Boolean): Unit = {
    this.selected = selected
  }
  def getGroup(): SegmentGroup = {
    group
  }
  def setGroup(group: SegmentGroup): Unit = {
    this.group = group
  }
  /**
   * 源的0秒在时间轴中的位置
   */
  @volatile private var origin: Long = 0L
  def getOrigin(): Long = {
    origin
  }
  def setOrigin(origin: Long): Unit = {
    this.origin = origin
  }
  //单写多读
  def offsetOrigin(offset: Long): Unit = {
    this.origin += offset
  }
  source.setSegment(this)
  actor = source.createSegActor(this)
  protected[timeline] def setTrack(track: Track): Unit = {
    this.track = track
  }

  /**
   * @param time 绝对时间
   */
  def get(time: Long): Frame = {
    val f = source.get(toLocalTime(time), track)
    if (f == null) return null
    f.withTime(time)
  }

  /**
   * 同步到指定时间
   * @param time 绝对时间
   */
  def sync(time: Long): Unit = {
    source.sync(toLocalTime(time), track)
  }
  def getActor(): SegActor = {
    java.util.Objects.requireNonNull(actor, "Segment actor is not initialized")
  }
  def toLocalTime(time: Long): Long = {
    time - origin
  }
  /** 该片段对应的媒体源总时长（微秒） */
  def getDuration(): Long = {
    source.getDuration()
  }
  def getTrack(): Track = {
    track
  }

  def getRange(): Range[java.lang.Long] = {
    range
  }
  /**获取拉伸时在时间轴上合法的最小起点*/
  def getMinStart(): Long = {
    Math.max(0, origin)
  }
  /**获取拉伸时在时间轴上合法的最大终点*/
  def getMaxEnd(): Long = {
    val duration = source.getDuration()
    if (duration == Long.MaxValue) return Long.MaxValue
    origin + duration
  }

  /**
   * 按轨道索引、再按片段起始时间排序（轨道越小越靠前，起始时间越小越靠前）
   */
  override def compareTo(o: Segment): Int = {
    val c = Integer.compare(trackIndex(), o.trackIndex())
    if (c != 0) return c
    java.lang.Long.compare(rangeStart(), o.rangeStart())
  }

  private def trackIndex(): Int = {
    val track = this.track
    if (track == null) Integer.MAX_VALUE else track.index
  }

  private def rangeStart(): Long = {
    val range = this.range
    if (range == null) Long.MaxValue else range.lowerEndpoint()
  }
  protected[timeline] def setRange(range: Range[java.lang.Long]): Unit = {
    if (range.equals(this.range)) return
    this.range = range
  }

  private def readObject(in: ObjectInputStream): Unit = {
    in.defaultReadObject()
    source.setSegment(this)
    actor = source.createSegActor(this)
  }
  override def iterator(): Iterator[Frame] = {
    new IteratorImpl()
  }
  class IteratorImpl extends Iterator[Frame] {
    private var time: Long = requireRange().lowerEndpoint() % source.getLengthPerExportFrame()
    override def hasNext(): Boolean = {
      time <= requireRange().upperEndpoint()
    }
    override def next(): Frame = {
      val f = java.util.Objects.requireNonNull(get(time), "Segment frame is unavailable")
      time += source.getLengthPerExportFrame()
      f
    }
  }
  override def copy(): Copyable = {
    duplicate()
  }

  override def duplicate(): Segment = {
    val savedGroup = group
    group = null
    val segment = super[Duplicatable].duplicate()
    group = savedGroup
    segment.range = range
    segment.source.onDuplicate(source)
    segment
  }
  /**
   * @author shan_luan_
   */
  def next(): Segment = {
    val track = requireTrack()
    val range = requireRange()
    val e = track.getSubRangeMapAsEntrySet(Range.atLeast(range.upperEndpoint()))
    val it = e.iterator()
    while (it.hasNext) {
      val next = it.next()
      return next.getValue
    }
    null
  }

  /**
   * @author shan_luan_
   */
  def prev(): Segment = {
    val track = requireTrack()
    val range = requireRange()
    track.get(range.lowerEndpoint(), -1, true)
  }
  def nextRange(): Range[java.lang.Long] = {
    val next = this.next()
    if (next != null) {
      next.requireRange()
    } else {
      Range.singleton(java.lang.Long.valueOf(Long.MaxValue))
    }
  }
  def prevRange(): Range[java.lang.Long] = {
    val prev = this.prev()
    if (prev != null) {
      prev.requireRange()
    } else {
      Range.singleton(java.lang.Long.valueOf(0L))
    }
  }

  private def requireTrack(): Track = {
    java.util.Objects.requireNonNull(track, "Segment is not attached to a track")
  }

  private def requireRange(): Range[java.lang.Long] = {
    java.util.Objects.requireNonNull(range, "Segment has no timeline range")
  }
}
