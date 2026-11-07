package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.{Segment, Track}
import com.lomekwi.cave.ui.editpanel.tlarea.{TlBlockSegmentActor, TlSegmentActor}

/**
 * 阻挡片段的源。
 */
@SerialVersionUID(1L)
class BlockSource extends Source[Frame] {

  override protected def produce(time: Long, track: Track, segment: Segment): Frame = {
    new GapFrame(track.index)
  }

  override def getLengthPerExportFrame: Long = 0L

  override def getDuration: Long = Long.MaxValue

  override def getDefaultDuration: Option[Long] = None

  override def displayName: String = ""

  override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
    new TlBlockSegmentActor(segment)
  }
}
