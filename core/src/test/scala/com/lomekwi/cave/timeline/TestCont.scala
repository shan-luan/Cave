package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Clip, Frame, Source, Segment}
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

/**
 * 测试用最小片段，仅提供拖拽/轨道逻辑测试所需的时序数据，不涉及真实编解码。
 */
class TestCont(duration0: Long) extends Clip(new TestCont.TestSource(duration0))

object TestCont {
  class TestFrame(trackIndex: Int) extends Frame(trackIndex) {
  }

  /** 按给定总时长产出空帧的最小源。 */
  private final class TestSource(duration: Long) extends Source[TestFrame] {
    override protected def produce(time: Long, track: Track, segment: Segment): TestFrame = {
      new TestFrame(track.index)
    }

    override def getLengthPerExportFrame: Long = {
      1
    }

    override def getDuration: Long = {
      duration
    }

    override def getDefaultDuration: Option[Long] = {
      Some(duration)
    }

    override def displayName: String = {
      "test"
    }

    override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
      new TlTestSegmentActor(segment)
    }
  }
}
