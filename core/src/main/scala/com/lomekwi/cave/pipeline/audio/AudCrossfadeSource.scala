package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.app.AppAudioOut
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.TransitionSource
import com.lomekwi.cave.resource.decoder.AudDecRes

/**
 * 声音叠化转场。两侧采样按等功率曲线交叉混合，转场中点对不相关素材保持响度平稳。
 */
@SerialVersionUID(1L)
class AudCrossfadeSource(from: Source[? <: AudFrame], to: Source[? <: AudFrame])
  extends TransitionSource[AudFrame, AudFrame](from, to) {

  override def mix(fromFrame: AudFrame, toFrame: AudFrame, progress: Float): AudFrame = {
    if (fromFrame == null) return toFrame
    if (toFrame == null) return fromFrame

    val trackIndex = fromFrame.trackIndex
    val result =
      if (frame == null || frame.trackIndex != trackIndex) new AudFrame(AppAudioOut.SAMPLE_RATE, trackIndex)
      else frame
    // 两侧的 samples 是各自解码器复用的缓冲，混合结果必须落在独立的缓冲上
    val samples =
      if (result.samples == null) {
        val s = new Array[Float](AudDecRes.FRAME_SIZE)
        result.samples = s
        s
      } else result.samples

    // 等功率交叉混合，转场中点两路权重各为 √2/2
    val fromGain = math.cos(progress * math.Pi / 2).toFloat
    val toGain = math.sin(progress * math.Pi / 2).toFloat
    val a = fromFrame.samples
    val b = toFrame.samples
    var i = 0
    while (i < samples.length) {
      samples(i) = a(i) * fromGain + b(i) * toGain
      i += 1
    }
    frame = result
    result
  }

  override def displayName: String = {
    "声音叠化"
  }
}
