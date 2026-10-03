package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.TransitionSource

/**
 * 叠化转场。前帧线性淡出、后帧线性淡入，帧经 [[BiRenderFrame]] 按透明度叠加。
 */
@SerialVersionUID(1L)
class CrossfadeSource(from: Source[? <: ImgFrame], to: Source[? <: ImgFrame])
  extends TransitionSource[ImgFrame, BiRenderFrame](from, to) {

  //FIXME 前后两段内容来自同一媒体资源时，两个源在相同轨道上共用同一个底层解码器，
  // 转场期间交替向它取帧来回定位，转场画面因此不流畅
  override def mix(fromFrame: ImgFrame, toFrame: ImgFrame, progress: Float): BiRenderFrame = {
    val trackIndex = (if (fromFrame != null) fromFrame else toFrame).trackIndex
    if (fromFrame != null) fromFrame.opacity = 1f - progress
    if (toFrame != null) toFrame.opacity = progress
    new BiRenderFrame(trackIndex, fromFrame, toFrame)
  }

  override def displayName: String = {
    "叠化"
  }
}
