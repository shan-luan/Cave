package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Track

import scala.reflect.ClassTag

/**
 * 测试用转场源。忽略前后两段源，总是产出空帧。
 */
@SerialVersionUID(1L)
class TestTransitionSource[T <: Frame](from: Source[? <: T], to: Source[? <: T])(using ClassTag[T])
  extends TransitionSource[T](from, to) {

  override protected def produce(time: Long, track: Track, segment: Segment[T]): T = {
    new GapFrame(track.index).asInstanceOf[T]
  }

  override def getLengthPerExportFrame: Long = {
    1
  }

  override def getDuration: Long = {
    Long.MaxValue
  }

  override def displayName: String = {
    "测试转场源"
  }
}
