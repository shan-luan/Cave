package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.timeline.{Segment, Timespan, Track}
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

//TODO:WIP
@SerialVersionUID(1L)
class NumSource extends Source[NumFrame] {
  override protected def produce(time: Timespan, track: Track, segment: Segment): NumFrame = {
    if (frame == null || frame.trackIndex != track.index) {
      frame = new NumFrame(track.index, segment.id)
    }
    frame
  }

  override def getLengthPerExportFrame: Timespan = {
    Timespan.SECOND
  }

  override def getDuration: Timespan = {
    Timespan.MaxValue
  }

  override def getDefaultDuration: Option[Timespan] = {
    Some(Timespan.SECOND * 5)
  }

  override def displayName: String = {
    ("数值源".in(Zh) | "Number Source".in(En)).str()
  }

  override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
    ???
  }
}
