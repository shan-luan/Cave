package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.util.Units.SECOND
import com.lomekwi.cave.pipeline.{Source, Segment}
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

//TODO:WIP
@SerialVersionUID(1L)
class NumSource extends Source[NumFrame] {
  override protected def produce(time: Long, track: Track, segment: Segment[NumFrame]): NumFrame = {
    if (frame == null || frame.trackIndex != track.index) {
      frame = new NumFrame(track.index, segment)
    }
    frame
  }

  override def getLengthPerExportFrame: Long = {
    SECOND
  }

  override def getDuration: Long = {
    Long.MaxValue
  }

  override def getDisplayName: String = {
    "数值源"
  }

  override def createTlSegmentActor(segment: Segment[?]): TlSegmentActor = {
    ???
  }
}
