package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.TransitionSource

/**
 * 叠化转场。后帧经 [[BiRenderFrame]] 以渐显的透明度叠在前帧之上。
 */
@SerialVersionUID(1L)
class CrossfadeSource(from: Source[? <: ImgFrame], to: Source[? <: ImgFrame])
  extends TransitionSource[ImgFrame, BiRenderFrame](from, to) {

  //FIXME 前后两段内容来自同一媒体资源时，两个源在相同轨道上共用同一个底层解码器，
  // 转场期间交替向它取帧来回定位，转场画面因此不流畅

  override def mix(fromFrame: ImgFrame, toFrame: ImgFrame, progress: Float): BiRenderFrame = {
    if (toFrame != null) toFrame.opacity = progress
    val trackIndex = (if (fromFrame != null) fromFrame else toFrame).trackIndex
    val result =
      if (frame == null) new BiRenderFrame(trackIndex, fromFrame, toFrame)
      else frame
    result.a = fromFrame
    result.b = toFrame
    frame = result
    result
  }

  override def displayName: String = {
    "叠化"
  }
}
