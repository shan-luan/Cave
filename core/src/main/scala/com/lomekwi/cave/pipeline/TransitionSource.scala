package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.{TlSegmentActor, TlTransitionActor}

import scala.reflect.ClassTag

/**
 * 转场源。由前后两段内容的源组合而成，是唯一输入与输出帧型分离的源。
 *
 * 转场的区间与两侧内容都由轨道持有，本类不保存任何布局数值，求值结果与轨道布局一致。
 *
 * 两侧交给 [[mix]] 的帧取自内容片段滤镜链的输出，链上滤镜不得改变帧的运行时类型。
 *
 * 实现具体效果只需覆盖 [[TransitionSource.mix]]，不要覆盖 [[produce]]。
 *
 * @tparam I 两侧内容源产出的帧型
 * @tparam O 转场自身产出的帧型
 */
@SerialVersionUID(1L)
abstract class TransitionSource[I <: Frame, O <: Frame](val from: Source[? <: I], val to: Source[? <: I])
  (using ClassTag[O]) extends Source[O] {

  // 求值时用 [[Track.getRange]]、[[Track.getOrigin]]、[[Track.transitionSides]] 反查轨道布局
  final override def canCreateTransitionWith(source: Source[?]): Boolean = false

  final override def createTransition(source: Source[?]): TransitionSource[? <: Frame, ? <: Frame] = {
    throw new UnsupportedOperationException("转场不能嵌套")
  }

  /**
   * 合成转场区间内的一帧。
   *
   * @param fromFrame 转场起点一侧内容的帧，progress 为 0 时独占画面；该侧此刻无帧时为 null
   * @param toFrame   转场终点一侧内容的帧，progress 趋近 1 时独占画面；该侧此刻无帧时为 null
   * @param progress  转场进度，区间 [0, 1)，从起点侧向终点侧推进
   * @return 合成帧。两侧不会同时为 null
   */
  protected def mix(fromFrame: I, toFrame: I, progress: Float): O

  /**
   * [[from]] 与 [[to]] 各自对应的内容片段（原始配对），以及方向标志。
   * 后段兜底构造的转场源 [[from]] 与 [[to]] 顺序互换，flipped 为 true 表示 [[from]] 指向终点侧。
   */
  private def rawSides(segment: Segment, track: Track): (Content, Content, Boolean) = {
    val sides = track.transitionSides(segment.asInstanceOf[Transition])
    require(sides != null, "转场源在轨道之外被求值: " + segment)
    val left = sides._1
    val right = sides._2
    if (left.source eq from) (left, right, false) else (right, left, true)
  }

  final override protected def produce(time: Long, track: Track, segment: Segment): O = {
    val (fromSeg, toSeg, flipped) = rawSides(segment, track)
    val abs = track.getOrigin(segment) + time
    val a = fromSeg.get(abs - track.getOrigin(fromSeg), track).asInstanceOf[I]
    val b = toSeg.get(abs - track.getOrigin(toSeg), track).asInstanceOf[I]
    // 两侧都无帧才交出无帧，单侧无帧原样交给 [[mix]]，由效果决定表现
    if (a == null && b == null) null.asInstanceOf[O]
    else {
      val progress = time.toFloat / (track.getRange(segment).hi - track.getRange(segment).lo)
      // [[mix]] 的视角固定为起点侧在前，flipped 时参数对调、进度翻转
      if (flipped) mix(b, a, 1f - progress) else mix(a, b, progress)
    }
  }

  override def sync(time: Long, track: Track, segment: Segment): Unit = {
    val (fromSeg, toSeg, _) = rawSides(segment, track)
    val abs = track.getOrigin(segment) + time
    fromSeg.sync(abs - track.getOrigin(fromSeg), track)
    toSeg.sync(abs - track.getOrigin(toSeg), track)
  }

  override def prefetch(): Unit = {
    from.prefetch()
    to.prefetch()
  }

  // 转场长度由轨道的重叠区决定，不从源上查
  override def getLengthPerExportFrame: Long = 1

  override def getDuration: Long = Long.MaxValue

  override def getDefaultDuration: Option[Long] = None

  override def createTlSegmentActor(segment: Segment): TlSegmentActor = new TlTransitionActor(segment)
}
