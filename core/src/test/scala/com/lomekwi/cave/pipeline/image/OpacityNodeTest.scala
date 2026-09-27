package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.graphics.g2d.Batch
import com.lomekwi.cave.pipeline.{Content, Frame, NodeRegistry, Segment, Source}
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

import OpacityNodeTest.*

/**
 * 验证 [[OpacityNode]] 沿滤镜链把透明度写到帧上，且注册表会为可渲染片段提供它。
 */
class OpacityNodeTest {

  @Test
  def default_keepsFrameOpaque(): Unit = {
    val segment = new OpCont
    segment.attach(new OpacityNode())

    assertEquals(1f, segment.get(0, null).opacity, 0f)
  }

  @Test
  def chain_appliesFrameOpacity(): Unit = {
    val segment = new OpCont
    val node = new OpacityNode()
    node.setOpacity(0.25)
    segment.attach(node)

    assertEquals(0.25f, segment.get(0, null).opacity, 0f)
  }

  @Test
  def chainedNodes_composeOpacity(): Unit = {
    val segment = new OpCont
    val a = new OpacityNode()
    a.setOpacity(0.5)
    val b = new OpacityNode()
    b.setOpacity(0.5)
    segment.attach(a)
    segment.attach(b)

    assertEquals(0.25f, segment.get(0, null).opacity, 0f)
  }

  @Test
  def registry_offersNodeForRenderableSegment(): Unit = {
    val registry = new NodeRegistry()
    val segment = new OpCont
    val names = (0 until registry.getCompatibleCount(segment))
      .map(i => registry.createCompatible(segment, i).name)

    assertTrue(names.contains("透明度"))
  }
}

object OpacityNodeTest {
  /** 最小可测的可渲染帧。 */
  private final class OpFrame extends Frame(-1) with Renderable {
    override def render(batch: Batch): Unit = {}
  }

  private final class OpCont extends Content[OpFrame](new OpSource)

  private final class OpSource extends Source[OpFrame] {
    override protected def produce(time: Long, track: Track, segment: Segment[OpFrame]): OpFrame = {
      new OpFrame
    }

    override def getLengthPerExportFrame: Long = {
      1
    }

    override def getDuration: Long = {
      Long.MaxValue
    }

    override def displayName: String = {
      "透明帧源"
    }

    override def createTlSegmentActor(segment: Segment[?]): TlSegmentActor = {
      null
    }
  }
}
