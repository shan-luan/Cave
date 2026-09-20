package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Segment
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.inspector.SourceActor
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor

import java.io.Serializable
import java.util
import scala.compiletime.uninitialized

/**
 * 帧源。仅应该被单个片段访问。自身是 filter 链的头：
 * 提供 FilterOut（链起点，输出 generate() 生成的帧）。
 *
 * @tparam T 帧类型
 */
@SerialVersionUID(1L)
abstract class Source[T <: Frame] extends Filter[T] with Serializable {
  @transient protected var frame: T = null.asInstanceOf[T]

  /** 链头输出端口：输出本源生成的最新帧，供第一个 filter 消费。 */
  final val headOut: FilterOut = addOutPort(new FilterOut {
    override def getData: T = {
      frame
    }

    override def getType: Class[? <: T] = {
      getFrameType
    }
  })

  private final val filters: util.List[Filter[? >: T]] = new FilterList[T](this)

  @transient private var segment: Segment = uninitialized

  /**
   * 同步到指定时间
   * @param time 绝对时间
   */
  def sync(time: Long, track: Track): Unit

  /**
   * 获取指定时间的产品：生成帧后沿 filter 链（端口连接）求值，
   * 返回链上最后一个 filter 的输出；无 filter 时返回原生帧。
   * @param time 绝对时间
   * @return 产品
   */
  final def get(time: Long, track: Track): T = {
    frame = generate(time, track)
    if (filters.isEmpty) frame
    else filters.get(filters.size() - 1).getFilterOut.getData.asInstanceOf[T]
  }

  /**
   * 建议进行预取数据的耗时操作。
   */
  def prefetch(): Unit = {}

  protected def generate(time: Long, track: Track): T

  def getFilters: util.List[Filter[? >: T]] = {
    filters
  }

  def attach(filter: Filter[? >: T]): Source[T] = {
    filters.add(filter)
    this
  }

  def getSegment: Segment = {
    segment
  }

  def setSegment(segment: Segment): Unit = {
    this.segment = segment
  }

  def getLengthPerExportFrame: Long
  /** 媒体源的总时长（微秒） */
  def getDuration: Long
  /**
   * 插入时间轴时片段使用的默认时长。时长无界（{@link #getDuration()} 为
   * {@link Long#MAX_VALUE}）的源必须返回有限值。
   */
  def getDefaultSegmentDuration: Long = {
    getDuration
  }
  def getDisplayName: String
  def getFrameType: Class[T]

  override def getType: Class[T] = {
    getFrameType
  }
  def onDuplicate(original: Source[?]): Unit = {
  }
  def getSourceActor: SourceActor = {
    new SourceActor(this)
  }
  def createSegActor(segment: Segment): SegActor

  override def getName: String = {
    getDisplayName
  }
}
