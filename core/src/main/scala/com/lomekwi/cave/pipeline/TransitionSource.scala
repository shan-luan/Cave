package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.{TlSegmentActor, TlTransitionActor}

import scala.reflect.ClassTag

/**
 * 转场源。由前后两段内容的源组合而成，两段源的类型不高于自身帧类型。
 *
 * 转场的区间与两侧内容都由轨道持有，本类不保存任何布局数值，
 * 求值时用 [[Track.getRange]]、[[Track.getOrigin]]、[[Track.transitionSides]] 反查，
 * 保证与轨道布局一致。
 *
 * 实现具体效果只需覆盖 [[TransitionSource.mix]]，不要覆盖 produce。
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

  /**
   * 合成转场区间内的一帧。
   *
   * @param fromFrame 转场起点一侧内容的帧，progress 为 0 时独占画面
   * @param toFrame   转场终点一侧内容的帧，progress 趋近 1 时独占画面
   * @param progress  转场进度，区间 [0, 1)，从起点侧向终点侧推进
   * @return 合成帧。两侧帧由框架保证非空
   */
  protected def mix(fromFrame: T, toFrame: T, progress: Float): T

  final override protected def produce(time: Long, track: Track, segment: Segment[T]): T = {
    val sides = track.transitionSides(segment.asInstanceOf[Transition[?]])
    require(sides != null, "转场源在轨道之外被求值: " + segment)
    val left = sides._1.asInstanceOf[Content[T]]
    val right = sides._2.asInstanceOf[Content[T]]
    // 后段兜底构造的转场源 from 与 to 顺序互换，这里统一还原成起点侧在前
    val flipped = !(left.source eq from)
    val fromSeg = if (flipped) right else left
    val toSeg = if (flipped) left else right
    val abs = track.getOrigin(segment) + time
    val a = from.asInstanceOf[Source[T]].generate(abs - track.getOrigin(fromSeg), track, fromSeg)
    val b = to.asInstanceOf[Source[T]].generate(abs - track.getOrigin(toSeg), track, toSeg)
    // 一侧暂时无帧时退化为另一侧，两侧都无帧才交出无帧
    if (a == null) b
    else if (b == null) a
    else {
      val progress = time.toFloat / (track.getRange(segment).hi - track.getRange(segment).lo)
      if (flipped) mix(b, a, 1f - progress) else mix(a, b, progress)
    }
  }

  // 转场长度由轨道的重叠区决定，不从源上查
  override def getLengthPerExportFrame: Long = 1

  override def getDuration: Long = Long.MaxValue

  override def createTlSegmentActor(segment: Segment[?]): TlSegmentActor = new TlTransitionActor(segment)
}
