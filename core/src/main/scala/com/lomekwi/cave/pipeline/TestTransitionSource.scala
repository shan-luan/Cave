package com.lomekwi.cave.pipeline

import scala.reflect.ClassTag

/**
 * 测试用转场源。忽略前后两帧，总是产出空帧。
 */
@SerialVersionUID(1L)
class TestTransitionSource[T <: Frame](from: Source[? <: T], to: Source[? <: T])(using ClassTag[T])
  extends TransitionSource[T](from, to) {

  override def mix(fromFrame: T, toFrame: T, progress: Float): T = {
    new GapFrame(fromFrame.trackIndex).asInstanceOf[T]
  }

  override def displayName: String = {
    "测试转场源"
  }
}
