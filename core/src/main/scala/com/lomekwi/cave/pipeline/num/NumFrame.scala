package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.timeline.Track

class NumFrame(track: Track, source: Source[?]) extends Frame(track, source) {
  private var `val`: Double = 0

  def this(track: Track) = {
    this(track, null)
  }

  def getVal(): Double = {
    `val`
  }

  def setVal(`val`: Double): Unit = {
    this.`val` = `val`
  }
}
