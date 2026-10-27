package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.project.Id

class NumFrame(trackIndex: Int, segmentId: Id) extends Frame(trackIndex, segmentId) {
  var value: Double = 0

  def this(trackIndex: Int) = {
    this(trackIndex, Id.noId())
  }
}
