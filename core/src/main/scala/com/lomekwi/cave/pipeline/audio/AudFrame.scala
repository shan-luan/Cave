package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Segment
import scala.compiletime.uninitialized

class AudFrame(sampleRate0: Int, trackIndex: Int, segment: Segment[?]) extends Frame(trackIndex, segment) {
  var samples: Array[Float] = uninitialized
  final val sampleRate: Int = sampleRate0
  var time: Long = 0

  def this(sampleRate: Int, trackIndex: Int) = {
    this(sampleRate, trackIndex, null)
  }

  override def close(): Unit = {
    super.close()
    samples = null
  }
}
