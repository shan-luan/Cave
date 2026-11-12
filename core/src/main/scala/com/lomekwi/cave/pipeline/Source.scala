package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.{Segment, Timespan, Track}
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import java.io.Serializable
import scala.reflect.ClassTag

/**
 * 帧源。filter 链的起点。
 *
 * @tparam T 帧类型
 */
@SerialVersionUID(1L)
abstract class Source[T <: Frame](using ClassTag[T]) extends Filter[T] with Serializable {
  @transient protected var frame: T = null.asInstanceOf[T]

  final val headOut: FilterOut = addOutPort(new FilterOut {
    override def getData(context: FilterChain | NodeGraph): T = {
      frame
    }

    override def getType(context: FilterChain | NodeGraph): Class[? <: T] = {
      classTag.runtimeClass.asInstanceOf[Class[? <: T]]
    }
  })

  /**
   * 生成 time 时刻的帧。
   * @param time 片段内时间
   * @param segment 宿主片段
   */
  final def generate(time: Timespan, track: Track, segment: Segment): T = {
    frame = produce(time, track, segment)
    frame
  }

  /**
   * 产出帧。
   * @param time 片段内时间
   * @param segment 宿主片段
   */
  protected def produce(time: Timespan, track: Track, segment: Segment): T

  /**
   * 同步到指定时间
   * @param time 片段内时间
   * @param segment 宿主片段
   */
  def sync(time: Timespan, track: Track, segment: Segment): Unit = {}

  def prefetch(): Unit = {}

  def getLengthPerExportFrame: Timespan

  /** 源的总时长（微秒） */
  def getDuration: Timespan

  /**
   * 插入时间轴时源使用的默认时长。
   */
  def getDefaultDuration: Option[Timespan]

  def displayName: String

  override def name: String = {
    displayName
  }

  /** 宿主片段在时间线上的可视化 actor。 */
  def createTlSegmentActor(segment: Segment): TlSegmentActor

  /** 本源与给定源能否构造转场。 */
  def canCreateTransitionWith(source: Source[?]): Boolean = false

  /**
   * 由本源与给定源构造转场源。
   */
  def createTransition(source: Source[?]): TransitionSource[? <: Frame, ? <: Frame] = {
    throw new UnsupportedOperationException(("本源不支持构造转场: ".in(Zh) | "This source does not support creating transitions: ".in(En)).str() + displayName)
  }

  // 目前没有调用点（duplicate 已移除）。
  def onDuplicate(original: Source[?]): Unit = {}
}
