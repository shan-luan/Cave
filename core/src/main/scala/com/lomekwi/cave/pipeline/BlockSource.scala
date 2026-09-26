package com.lomekwi.cave.pipeline

import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.{TlBlockSegmentActor, TlSegmentActor}

/**
 * 阻挡片段的源。它填充时间轴 0 点左侧，使左边界由普通障碍表达，
 * 拖拽与裁切因此不必再单独判断"不能小于 0"。
 */
@SerialVersionUID(1L)
class BlockSource extends Source[Frame] {

  override protected def produce(time: Long, track: Track, segment: Segment[Frame]): Frame = {
    new GapFrame(track.index)
  }

  override def getLengthPerExportFrame: Long = 0L

  override def getDuration: Long = Long.MaxValue

  override def getDisplayName: String = ""

  override def createTlSegmentActor(segment: Segment[?]): TlSegmentActor = {
    new TlBlockSegmentActor(segment)
  }
}
