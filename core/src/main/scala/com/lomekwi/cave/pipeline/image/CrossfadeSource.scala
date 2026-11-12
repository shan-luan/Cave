package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.TransitionSource
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

/**
 * 叠化转场。
 */
@SerialVersionUID(1L)
class CrossfadeSource(from: Source[? <: ImgFrame], to: Source[? <: ImgFrame])
  extends TransitionSource[ImgFrame, BiRenderFrame](from, to) {

  override def mix(fromFrame: ImgFrame, toFrame: ImgFrame, progress: Float): BiRenderFrame = {
    if (toFrame != null) toFrame.opacity *= progress
    val trackIndex = (if (fromFrame != null) fromFrame else toFrame).trackIndex
    val result =
      if (frame == null) new BiRenderFrame(trackIndex, fromFrame, toFrame)
      else frame
    result.a = fromFrame
    result.b = toFrame
    result.opacity = 1f
    frame = result
    result
  }

  override def displayName: String = {
    ("叠化".in(Zh) | "Crossfade".in(En)).str()
  }
}
