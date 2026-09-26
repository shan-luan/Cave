package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.util.Units.SECOND
import com.lomekwi.cave.pipeline.{Generator, Source}
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.TlSrcActor

//TODO:WIP
@SerialVersionUID(1L)
class NumGenerator extends Generator[NumFrame] {
  override protected def produce(time: Long, track: Track, source: Source[NumFrame]): NumFrame = {
    if (frame == null || frame.trackIndex != track.index) {
      frame = new NumFrame(track.index, source)
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

  override def createTlSrcActor(source: Source[?]): TlSrcActor = {
    ???
  }
}
