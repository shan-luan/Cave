package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Content
import com.lomekwi.cave.ui.editpanel.tlarea.TlSrcActor

/**
 * 测试用最小帧源，仅提供拖拽/轨道逻辑测试所需的时序数据，不涉及真实编解码。
 */
class TestCont(duration0: Long) extends Content[TestCont.TestFrame] {
  private final val duration: Long = duration0

  override def sync(time: Long, track: Track): Unit = {
  }

  override protected def generate(time: Long, track: Track): TestCont.TestFrame = {
    new TestCont.TestFrame(track)
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

  override def createTlSrcActor(): TlSrcActor = {
    new TlTestSrcActor(this)
  }
}

object TestCont {
  class TestFrame(track: Track) extends Frame(track) {
  }
}
