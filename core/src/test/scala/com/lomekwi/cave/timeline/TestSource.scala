package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.ui.editpanel.tlarea.TlSrcActor

/**
 * 测试用最小帧源：仅提供拖拽/轨道逻辑测试所需的时序数据，不涉及真实编解码。
 */
class TestSource(duration0: Long) extends Source[TestSource.TestFrame] {
  private final val duration: Long = duration0

  override def sync(time: Long, track: Track): Unit = {
    // no-op
  }

  override protected def generate(time: Long, track: Track): TestSource.TestFrame = {
    new TestSource.TestFrame(track)
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

object TestSource {
  class TestFrame(track: Track) extends Frame(track) {
  }
}
