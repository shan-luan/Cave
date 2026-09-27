package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Segment

class NumFrame(trackIndex: Int, segment: Segment[?]) extends Frame(trackIndex, segment) {
  var value: Double = 0

  def this(trackIndex: Int) = {
    this(trackIndex, null)
  }
}
