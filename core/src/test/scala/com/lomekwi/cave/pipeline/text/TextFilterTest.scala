package com.lomekwi.cave.pipeline.text

import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.project.{IdSource, TestIds}
import com.lomekwi.cave.timeline.{Boundless, Segment, Timespan, Track}
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

import TextFilterTest.*

/**
 * 验证 [[TextFilter]] 对帧文本的正则替换行为，且注册表只为文本片段提供它。
 */
class TextFilterTest extends munit.FunSuite {
  given IdSource = TestIds

  test("默认正则保留原文") {
    var segment: Segment = new TxtCont
    segment = segment.attach(new TextFilter())

    assertEquals(segment.get(Timespan.Zero, null).asInstanceOf[TextFrame].getText, ORIGIN)
  }

  test("全部替换时重写所有匹配") {
    var segment: Segment = new TxtCont
    val filter = new TextFilter()
    segment = segment.attach(filter)
    segment = segment.setFilterDefault(filter.regexIn, "o")
    segment = segment.setFilterDefault(filter.replaceIn, "0")

    assertEquals(segment.get(Timespan.Zero, null).asInstanceOf[TextFrame].getText, "hell0 w0rld")
  }

  test("无匹配时保留原文") {
    var segment: Segment = new TxtCont
    val filter = new TextFilter()
    segment = segment.attach(filter)
    segment = segment.setFilterDefault(filter.regexIn, "z")
    segment = segment.setFilterDefault(filter.replaceIn, "!")

    assertEquals(segment.get(Timespan.Zero, null).asInstanceOf[TextFrame].getText, ORIGIN)
  }

  test("正则非法时抛异常") {
    var segment: Segment = new TxtCont
    val filter = new TextFilter()
    segment = segment.attach(filter)
    segment = segment.setFilterDefault(filter.regexIn, "[")
    segment = segment.setFilterDefault(filter.replaceIn, "!")

    intercept[RuntimeException] {
      segment.get(Timespan.Zero, null)
    }
  }

  test("正则变化使已编译模式失效") {
    var segment: Segment = new TxtCont
    val filter = new TextFilter()
    segment = segment.attach(filter)
    segment = segment.setFilterDefault(filter.regexIn, "o")
    segment = segment.setFilterDefault(filter.replaceIn, "0")
    segment.get(Timespan.Zero, null)

    segment = segment.setFilterDefault(filter.regexIn, "l")
    segment = segment.setFilterDefault(filter.replaceIn, "L")

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
      // 真实源在每次产出时把帧恢复到基线，替换的语义依赖这一点
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
}
