package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Source
import scala.compiletime.uninitialized

class AudFrame(sampleRate0: Int, trackIndex: Int, source: Source[?]) extends Frame(trackIndex, source) {
  private var samples: Array[Float] = uninitialized
  private final val sampleRate: Int = sampleRate0
  private var time: Long = 0

  def this(sampleRate: Int, trackIndex: Int) = {
    this(sampleRate, trackIndex, null)
  }

  def getSamples: Array[Float] = {
    samples
  }

  def setSamples(samples: Array[Float]): AudFrame = {
    this.samples = samples
    this
  }

  def getSampleRate: Int = {
    sampleRate
  }

  def getTime: Long = {
    time
  }

  def setTime(time: Long): AudFrame = {
    this.time = time
    this
  }

  override def close(): Unit = {
    super.close()
    samples = null
  }
}
