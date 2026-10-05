package com.lomekwi.cave.pipeline.text

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.lomekwi.cave.pipeline.{Boundless, Frame, NodeRegistry, Segment, Source}
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test

import TextFilterTest.*

/**
 * 验证 [[TextFilter]] 对帧文本的正则替换行为，且注册表只为文本片段提供它。
 */
class TextFilterTest {

  @Test
  def defaultRegex_keepsText(): Unit = {
    val segment = new TxtCont
    segment.attach(new TextFilter())

    assertEquals(ORIGIN, segment.get(0, null).asInstanceOf[TextFrame].getText)
  }

  @Test
  def replaceAll_rewritesMatches(): Unit = {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("o")
    filter.setReplacement("0")
    segment.attach(filter)

    assertEquals("hell0 w0rld", segment.get(0, null).asInstanceOf[TextFrame].getText)
  }

  @Test
  def unmatchedRegex_keepsText(): Unit = {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("z")
    filter.setReplacement("!")
    segment.attach(filter)

    assertEquals(ORIGIN, segment.get(0, null).asInstanceOf[TextFrame].getText)
  }

  @Test
  def invalidRegex_keepsText(): Unit = {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("[")
    filter.setReplacement("!")
    segment.attach(filter)

    assertEquals(ORIGIN, segment.get(0, null).asInstanceOf[TextFrame].getText)
  }

  @Test
  def regexChange_invalidatesCompiledPattern(): Unit = {
    val segment = new TxtCont
    val filter = new TextFilter()
    filter.setRegex("o")
    filter.setReplacement("0")
    segment.attach(filter)
    segment.get(0, null)

    filter.setRegex("l")
    filter.setReplacement("L")

    assertEquals("heLLo worLd", segment.get(0, null).asInstanceOf[TextFrame].getText)
  }

  @Test
  def registry_offersNodeForTextSegment(): Unit = {
    val registry = new NodeRegistry()
    val segment = new TxtCont
    val names = (0 until registry.getCompatibleCount(segment))
      .map(i => registry.createCompatible(segment, i).name)

    assertTrue(names.contains("文本替换"))
  }

  @Test
  def registry_hidesNodeFromRenderableSegment(): Unit = {
    val registry = new NodeRegistry()
    val segment = new RendCont
    val names = (0 until registry.getCompatibleCount(segment))
      .map(i => registry.createCompatible(segment, i).name)

    assertFalse(names.contains("文本替换"))
  }
}

object TextFilterTest {
  private final val ORIGIN = "hello world"

  private final class TxtCont extends Boundless(new TxtSource)

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

  private final class RendFrame extends Frame(-1) with com.lomekwi.cave.pipeline.image.Renderable {
    override def render(batch: Batch): Unit = {}

    override protected def createActor(): Actor = null
  }

  private final class RendCont extends Boundless(new RendSource)

  private final class RendSource extends Source[RendFrame] {
    override protected def produce(time: Long, track: Track, segment: Segment): RendFrame = {
      if (frame == null) {
        frame = new RendFrame
      }
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
      "渲染帧源"
    }

    override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
      null
    }
  }
}
