package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.project.Id
import scala.compiletime.uninitialized

class AudFrame(sampleRate0: Int, trackIndex: Int, segmentId: Id) extends Frame(trackIndex, segmentId) {
  var samples: Array[Float] = uninitialized
  final val sampleRate: Int = sampleRate0
  var time: Long = 0

  def this(sampleRate: Int, trackIndex: Int) = {
    this(sampleRate, trackIndex, Id.noId())
  }

  override def close(): Unit = {
    super.close()
    samples = null
  }
}
