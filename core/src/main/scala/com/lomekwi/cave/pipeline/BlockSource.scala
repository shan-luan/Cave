package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.{Segment, Timespan, Track}
import com.lomekwi.cave.ui.editpanel.tlarea.{TlBlockSegmentActor, TlSegmentActor}

/**
 * 阻挡片段的源。
 */
@SerialVersionUID(1L)
class BlockSource extends Source[Frame] {

  override protected def produce(time: Timespan, track: Track, segment: Segment): Frame = {
    new GapFrame(track.index)
  }

  override def getLengthPerExportFrame: Timespan = Timespan.Zero

  override def getDuration: Timespan = Timespan.MaxValue

  override def getDefaultDuration: Option[Timespan] = None

  override def displayName: String = ""

  override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
    new TlBlockSegmentActor(segment)
  }
}
