package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.{Content, Instant, Segment, Timespan, Track, Transition}
import com.lomekwi.cave.ui.editpanel.tlarea.{TlSegmentActor, TlTransitionActor}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import scala.reflect.ClassTag

/**
 * 转场源。由前后两段内容的源组合而成.
 *
 * @tparam I 两侧内容源产出的帧型
 * @tparam O 转场自身产出的帧型
 */
@SerialVersionUID(1L)
abstract class TransitionSource[I <: Frame, O <: Frame](val from: Source[? <: I], val to: Source[? <: I])
  (using ClassTag[O]) extends Source[O] {

  final override def canCreateTransitionWith(source: Source[?]): Boolean = false

  final override def createTransition(source: Source[?]): TransitionSource[? <: Frame, ? <: Frame] = {
    throw new UnsupportedOperationException(("转场不能嵌套".in(Zh) | "Transitions cannot be nested".in(En)).str())
  }

  /**
   * 合成转场区间内的一帧。
   *
   * @param fromFrame 转场起点一侧内容的帧
   * @param toFrame   转场终点一侧内容的帧
   * @param progress  转场进度，[0, 1)
   * @return 合成后的帧。
   */
  protected def mix(fromFrame: I|Null, toFrame: I|Null, progress: Float): O

  /**
   * [[from]] 与 [[to]] 各自对应的内容片段（原始配对），以及方向标志。
   * 后段兜底构造的转场源 [[from]] 与 [[to]] 顺序互换，flipped 为 true 表示 [[from]] 指向终点侧。
   */
  private def rawSides(segment: Segment, track: Track): (Content, Content, Boolean) = {
    val sides = track.transitionSides(segment.asInstanceOf[Transition])
    require(sides.isDefined, ("转场源在轨道之外被求值: ".in(Zh) | "Transition source evaluated outside a track: ".in(En)).str() + segment)
    val (left, right) = sides.get
    if (left.source eq from) (left, right, false) else (right, left, true)
  }

  final override protected def produce(time: Timespan, track: Track, segment: Segment): O = {
    val (fromSeg, toSeg, flipped) = rawSides(segment, track)
    val abs: Instant = track.getOrigin(segment) + time
    val a = fromSeg.get(abs - track.getOrigin(fromSeg), track).asInstanceOf[I]
    val b = toSeg.get(abs - track.getOrigin(toSeg), track).asInstanceOf[I]
    if (a == null && b == null) null.asInstanceOf[O]
    else {
      val progress = time.value.toFloat / track.interval(segment).len.value
      if (flipped) mix(b, a, 1f - progress) else mix(a, b, progress)
    }
  }

  override def sync(time: Timespan, track: Track, segment: Segment): Unit = {
    val (fromSeg, toSeg, _) = rawSides(segment, track)
    val abs: Instant = track.getOrigin(segment) + time
    fromSeg.sync(abs - track.getOrigin(fromSeg), track)
    toSeg.sync(abs - track.getOrigin(toSeg), track)
  }

  override def prefetch(): Unit = {
    from.prefetch()
    to.prefetch()
  }

  override def getLengthPerExportFrame: Timespan = Timespan(1)

  override def getDuration: Timespan = Timespan.MaxValue

  override def getDefaultDuration: Option[Timespan] = None

  override def createTlSegmentActor(segment: Segment): TlSegmentActor = new TlTransitionActor(segment)
}
