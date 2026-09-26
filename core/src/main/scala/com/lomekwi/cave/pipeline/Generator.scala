package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.TlSrcActor

import java.io.Serializable
import scala.reflect.ClassTag

/**
 * 帧生成器。filter 链的起点，自身没有 FilterIn，
 * 输出 produce() 生成的帧供第一个 filter 消费。
 *
 * 驱动（sync/onStepOut/prefetch）与时长等元数据也由它承担，
 * [[Source]] 只做这两者的持有与对外门面。
 *
 * @tparam T 帧类型
 */
@SerialVersionUID(1L)
abstract class Generator[T <: Frame](using ClassTag[T]) extends Filter[T] with Serializable {
  @transient protected var frame: T = null.asInstanceOf[T]

  /** 链头输出端口，输出本源生成的最新帧供第一个 filter 消费。 */
  final val headOut: FilterOut = addOutPort(new FilterOut {
    override def getData: T = {
      frame
    }

    override def getType: Class[? <: T] = {
      classTag.runtimeClass.asInstanceOf[Class[? <: T]]
    }
  })

  /**
   * 生成 time 时刻的帧，并更新链头输出。
   * @param time 源内时间
   * @param source 宿主源
   */
  final def generate(time: Long, track: Track, source: Source[T]): T = {
    frame = produce(time, track, source)
    frame
  }

  /**
   * 产出帧。返回 null 表示此刻无帧。
   * @param time 源内时间
   * @param source 宿主源
   */
  protected def produce(time: Long, track: Track, source: Source[T]): T

  /**
   * 同步到指定时间
   * @param time 源内时间
   */
  def sync(time: Long, track: Track): Unit = {}

  /**
   * 播放头离开本源时调用。自然播放越过源终点，或 seek 使播放头落到源区间之外。
   * @param time 源内时间，即离开时播放头所在的源内位置
   */
  def onStepOut(time: Long, track: Track): Unit = {}

  def prefetch(): Unit = {}

  def getLengthPerExportFrame: Long

  /** 媒体源的总时长（微秒） */
  def getDuration: Long

  /**
   * 插入时间轴时源使用的默认时长。时长无界（[[Generator.getDuration]] 为
   * [[Long.MAX_VALUE]]）的源必须返回有限值。
   */
  def getDefaultDuration: Long = {
    getDuration
  }

  def getDisplayName: String

  override def getName: String = {
    getDisplayName
  }

  /** 本源在时间线上的可视化 actor。 */
  def createTlSrcActor(source: Source[?]): TlSrcActor

  def onDuplicate(original: Generator[?]): Unit = {}
}
