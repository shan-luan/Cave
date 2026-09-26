package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Content, Frame, Generator, Source}
import com.lomekwi.cave.ui.editpanel.tlarea.TlSrcActor

/**
 * 测试用最小帧源，仅提供拖拽/轨道逻辑测试所需的时序数据，不涉及真实编解码。
 */
class TestCont(duration0: Long) extends Content[TestCont.TestFrame](new TestCont.TestGenerator(duration0))

object TestCont {
  class TestFrame(trackIndex: Int) extends Frame(trackIndex) {
  }

  /** 按给定总时长产出空帧的最小生成器。 */
  private final class TestGenerator(duration: Long) extends Generator[TestFrame] {
    override protected def produce(time: Long, track: Track, source: Source[TestFrame]): TestFrame = {
      new TestFrame(track.index)
    }

    override def getLengthPerExportFrame: Long = {
      1
    }

    override def getDuration: Long = {
      duration
    }

    override def getDisplayName: String = {
      "test"
    }

    override def createTlSrcActor(source: Source[?]): TlSrcActor = {
      new TlTestSrcActor(source)
    }
  }
}
