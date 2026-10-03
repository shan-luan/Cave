package com.lomekwi.cave.pipeline

import scala.reflect.ClassTag

/**
 * 测试用转场源。忽略前后两帧，总是产出空帧。
 */
@Deprecated(forRemoval = true)
@SerialVersionUID(1L)
class TestTransitionSource[I <: Frame, O <: Frame](from: Source[? <: I], to: Source[? <: I])(using ClassTag[O])
  extends TransitionSource[I, O](from, to) {

  override def mix(fromFrame: I, toFrame: I, progress: Float): O = {
    new GapFrame((if (fromFrame != null) fromFrame else toFrame).trackIndex).asInstanceOf[O]
  }

  override def displayName: String = {
    "测试转场源"
  }
}
