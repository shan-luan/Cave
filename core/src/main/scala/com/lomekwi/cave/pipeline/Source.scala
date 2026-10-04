package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

import java.io.Serializable
import scala.reflect.ClassTag

/**
 * 帧源。filter 链的起点，自身没有 [[Filter.filterIn]]，
 * 输出 [[produce]] 生成的帧供第一个 filter 消费。
 *
 * 驱动（[[sync]]/[[onStepOut]]/[[prefetch]]）与时长等元数据也由它承担，
 * [[Segment]] 只做这两者的持有与对外门面。
 *
 * @tparam T 帧类型
 */
@SerialVersionUID(1L)
abstract class Source[T <: Frame](using ClassTag[T]) extends Filter[T] with Serializable {
  @transient protected var frame: T = null.asInstanceOf[T]

  /** 链头输出端口，输出源生成的最新帧供第一个 filter 消费。 */
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
   * @param time 片段内时间
   * @param segment 宿主片段
   */
  final def generate(time: Long, track: Track, segment: Segment): T = {
    frame = produce(time, track, segment)
    frame
  }

  /**
   * 产出帧。返回 null 表示此刻无帧。
   * @param time 片段内时间
   * @param segment 宿主片段
   */
  protected def produce(time: Long, track: Track, segment: Segment): T

  /**
   * 同步到指定时间
   * @param time 片段内时间
   * @param segment 宿主片段
   */
  def sync(time: Long, track: Track, segment: Segment): Unit = {}

  /**
   * 播放头离开本片段时调用。自然播放越过片段终点，或 seek 使播放头落到片段区间之外。
   * @param time 片段内时间，即离开时播放头所在的片段内位置
   * @param segment 宿主片段
   */
  def onStepOut(time: Long, track: Track, segment: Segment): Unit = {}

  def prefetch(): Unit = {}

  def getLengthPerExportFrame: Long

  /** 源的总时长（微秒） */
  def getDuration: Long

  /**
   * 插入时间轴时源使用的默认时长；没有可放置默认时长的源（如阻挡片段）返回 None。
   * 由 [[Segment.getDefaultDuration]] 解包，None 在放置路径上抛 [[IllegalStateException]]。
   */
  def getDefaultDuration: Option[Long]

  def displayName: String

  override def name: String = {
    displayName
  }

  /** 宿主片段在时间线上的可视化 actor。 */
  def createTlSegmentActor(segment: Segment): TlSegmentActor

  /** 本源与给定源能否构造转场。默认不支持，支持的源覆写本方法与 [[createTransition]]。 */
  def canCreateTransitionWith(source: Source[?]): Boolean = false

  /**
   * 由本源与给定源构造转场源。
   * 要求 [[canCreateTransitionWith]] 为 true，否则抛 [[UnsupportedOperationException]]。
   */
  def createTransition(source: Source[?]): TransitionSource[? <: Frame, ? <: Frame] = {
    throw new UnsupportedOperationException("本源不支持构造转场: " + displayName)
  }

  def onDuplicate(original: Source[?]): Unit = {}
}
