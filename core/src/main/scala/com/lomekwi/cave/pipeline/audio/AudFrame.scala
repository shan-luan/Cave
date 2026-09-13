package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.timeline.Track

class AudFrame(sampleRate0: Int, channels0: Int, track: Track, source: Source[?]) extends Frame(track, source) {
  private var samples: Array[Float] = null
  private final val sampleRate: Int = sampleRate0
  private final val channels: Int = channels0
  private var time: Long = 0

  def this(sampleRate: Int, channels: Int, track: Track) = {
    this(sampleRate, channels, track, null)
  }

  def getSamples(): Array[Float] = {
    samples
  }

  def setSamples(samples: Array[Float]): AudFrame = {
    this.samples = samples
    this
  }

  def getSampleRate(): Int = {
    sampleRate
  }

  def getTime(): Long = {
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
