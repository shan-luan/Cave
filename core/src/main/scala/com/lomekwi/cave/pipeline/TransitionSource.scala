package com.lomekwi.cave.pipeline

import com.lomekwi.cave.ui.editpanel.tlarea.{TlSegmentActor, TlTransitionActor}

import scala.reflect.ClassTag

/**
 * 转场源。由前后两段内容的源组合而成，两段源的类型不高于自身帧类型。
 *
 * @tparam T 帧类型
 */
@SerialVersionUID(1L)
abstract class TransitionSource[T <: Frame](val from: Source[? <: T], val to: Source[? <: T])
  (using ClassTag[T]) extends Source[T] {

  final override def canCreateTransitionWith(source: Source[?]): Boolean = false

  final override def createTransition(source: Source[?]): TransitionSource[T] = {
    throw new UnsupportedOperationException("转场不能嵌套")
  }

    override def createTlSegmentActor(segment: Segment[?]): TlSegmentActor = new TlTransitionActor(segment)
}
