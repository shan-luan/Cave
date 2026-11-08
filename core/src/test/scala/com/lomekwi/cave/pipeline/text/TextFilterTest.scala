package com.lomekwi.cave.pipeline.text

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.lomekwi.cave.pipeline.{Frame, Source}
import com.lomekwi.cave.project.TestIds
import com.lomekwi.cave.timeline.{Boundless, Segment, Timespan, Track}
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

import TextFilterTest.*

/**
 * 验证 [[TextFilter]] 对帧文本的正则替换行为。
 */
class TextFilterTest extends munit.FunSuite {

  test("默认正则保留原文") {
    val segment = new TxtCont
    segment.attach(new TextFilter())

    assertEquals(segment.get(Timespan.Zero, null).asInstanceOf[TextFrame].getText, ORIGIN)
  }

  test("全部替换时重写所有匹配") {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("o")
    filter.setReplacement("0")
    segment.attach(filter)

    assertEquals(segment.get(Timespan.Zero, null).asInstanceOf[TextFrame].getText, "hell0 w0rld")
  }

  test("无匹配时保留原文") {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("z")
    filter.setReplacement("!")
    segment.attach(filter)

    assertEquals(segment.get(Timespan.Zero, null).asInstanceOf[TextFrame].getText, ORIGIN)
  }

  test("正则非法时包装后抛出") {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("[")
    filter.setReplacement("!")
    segment.attach(filter)

    intercept[RuntimeException] {
      segment.get(Timespan.Zero, null)
    }
  }

  test("正则变化使已编译模式失效") {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("o")
    filter.setReplacement("0")
    segment.attach(filter)
    segment.get(Timespan.Zero, null)

    filter.setRegex("l")
    filter.setReplacement("L")

    assertEquals(segment.get(Timespan.Zero, null).asInstanceOf[TextFrame].getText, "heLLo worLd")
  }
}

object TextFilterTest {
  private val ORIGIN = "hello world"

  private final class TxtCont extends Boundless(TestIds.nextId(), new TxtSource)

  private final class TxtSource extends Source[TextFrame] {
    override protected def produce(time: Timespan, track: Track, segment: Segment): TextFrame = {
      if (frame == null) {
        frame = new TextFrame(-1)
      }
      frame.setText(ORIGIN)
      frame
    }

    override def getLengthPerExportFrame: Timespan = {
      Timespan(1)
    }

    override def getDuration: Timespan = {
      Timespan.MaxValue
    }

    override def getDefaultDuration: Option[Timespan] = {
      None
    }

    override def displayName: String = {
      "文本帧源"
    }

    override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
      null
    }
  }

  private final class RendFrame extends Frame(-1) with com.lomekwi.cave.pipeline.image.Renderable {
    override def render(batch: Batch): Unit = {}

    override protected def createActor(): Actor = null
  }

  private final class RendCont extends Boundless(TestIds.nextId(), new RendSource)

  private final class RendSource extends Source[RendFrame] {
    override protected def produce(time: Timespan, track: Track, segment: Segment): RendFrame = {
      if (frame == null) {
        frame = new RendFrame
      }
      frame
    }

    override def getLengthPerExportFrame: Timespan = {
      Timespan(1)
    }

    override def getDuration: Timespan = {
      Timespan.MaxValue
    }

    override def getDefaultDuration: Option[Timespan] = {
      None
    }

    override def displayName: String = {
      "渲染帧源"
    }

    override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
      null
    }
  }
}
