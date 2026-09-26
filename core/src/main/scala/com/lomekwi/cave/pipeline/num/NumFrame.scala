package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Segment

class NumFrame(trackIndex: Int, segment: Segment[?]) extends Frame(trackIndex, segment) {
  private var value: Double = 0

  def this(trackIndex: Int) = {
    this(trackIndex, null)
  }

  def getVal: Double = {
    value
  }

  def setVal(value: Double): Unit = {
    this.value = value
  }
}
