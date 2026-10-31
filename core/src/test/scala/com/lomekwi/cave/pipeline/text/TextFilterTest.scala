package com.lomekwi.cave.pipeline.text

import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.project.TestIds
import com.lomekwi.cave.timeline.{Boundless, Segment, Track}
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

import TextFilterTest.*

/**
 * 验证 [[TextFilter]] 对帧文本的正则替换行为，且注册表只为文本片段提供它。
 */
class TextFilterTest extends munit.FunSuite {

  test("默认正则保留原文") {
    val segment = new TxtCont
    segment.attach(new TextFilter())

    assertEquals(segment.get(0, null).asInstanceOf[TextFrame].getText, ORIGIN)
  }

  test("全部替换时重写所有匹配") {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("o")
    filter.setReplacement("0")
    segment.attach(filter)

    assertEquals(segment.get(0, null).asInstanceOf[TextFrame].getText, "hell0 w0rld")
  }

  test("无匹配时保留原文") {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("z")
    filter.setReplacement("!")
    segment.attach(filter)

    assertEquals(segment.get(0, null).asInstanceOf[TextFrame].getText, ORIGIN)
  }

  test("正则非法时保留原文") {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("[")
    filter.setReplacement("!")
    segment.attach(filter)

    assertEquals(segment.get(0, null).asInstanceOf[TextFrame].getText, ORIGIN)
  }

  test("正则变化使已编译模式失效") {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("o")
    filter.setReplacement("0")
    segment.attach(filter)
    segment.get(0, null)

    filter.setRegex("l")
    filter.setReplacement("L")

    assertEquals(segment.get(0, null).asInstanceOf[TextFrame].getText, "heLLo worLd")
  }
}

object TextFilterTest {
  private val ORIGIN = "hello world"

  private final class TxtCont extends Boundless(TestIds.nextId(), new TxtSource)

  private final class TxtSource extends Source[TextFrame] {
    override protected def produce(time: Long, track: Track, segment: Segment): TextFrame = {
      if (frame == null) {
        frame = new TextFrame(-1)
      }
      // 真实源在每次产出时把帧恢复到基线，替换的语义依赖这一点
      frame.setText(ORIGIN)
      frame
    }

    override def getLengthPerExportFrame: Long = {
      1
    }

    override def getDuration: Long = {
      Long.MaxValue
    }

    override def getDefaultDuration: Option[Long] = {
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
