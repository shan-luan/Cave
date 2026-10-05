package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.pipeline.{Boundless, NodeRegistry, Segment, Source}
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

import GainNodeTest.*

/**
 * 验证 [[GainNode]] 沿滤镜链把音量乘到采样点上，且注册表会为音频片段提供它。
 */
class GainNodeTest {

  @Test
  def default_keepsSamplesUnchanged(): Unit = {
    val segment = new AudCont
    segment.attach(new GainNode())

    assertSamples(segment, BASELINE)
  }

  @Test
  def gain_scalesSamples(): Unit = {
    val segment = new AudCont
    val node = new GainNode()
    node.setGain(0.5)
    segment.attach(node)

    assertSamples(segment, Array(0.05f, -0.1f, 0.25f))
  }

  @Test
  def gain_notClamped(): Unit = {
    val segment = new AudCont
    val node = new GainNode()
    node.setGain(3.0)
    segment.attach(node)

    assertSamples(segment, Array(0.3f, -0.6f, 1.5f))
  }

  @Test
  def chainedNodes_composeGain(): Unit = {
    val segment = new AudCont
    val a = new GainNode()
    a.setGain(2.0)
    val b = new GainNode()
    b.setGain(0.25)
    segment.attach(a)
    segment.attach(b)

    assertSamples(segment, Array(0.05f, -0.1f, 0.25f))
  }

  @Test
  def repeatedEvaluation_staysStable(): Unit = {
    val segment = new AudCont
    val node = new GainNode()
    node.setGain(2.0)
    segment.attach(node)

    assertSamples(segment, Array(0.2f, -0.4f, 1.0f))
    assertSamples(segment, Array(0.2f, -0.4f, 1.0f))
    assertSamples(segment, Array(0.2f, -0.4f, 1.0f))
  }

  @Test
  def registry_offersNodeForAudioSegment(): Unit = {
    val registry = new NodeRegistry()
    val segment = new AudCont
    val names = (0 until registry.getCompatibleCount(segment))
      .map(i => registry.createCompatible(segment, i).name)

    assertTrue(names.contains("音量"))
  }

  private def assertSamples(segment: Segment, expected: Array[Float]): Unit = {
    val frame = segment.get(0, null).asInstanceOf[AudFrame]
    assertEquals(expected.length, frame.samples.length)
    for (i <- expected.indices) {
      assertEquals(expected(i), frame.samples(i), 1e-6f)
    }
  }
}

object GainNodeTest {
  private final val BASELINE = Array(0.1f, -0.2f, 0.5f)

  private final class AudCont extends Boundless(new AudTestSource)

  /** 模拟真实音频源：复用同一帧，每次产出时把采样恢复到基线。 */
  private final class AudTestSource extends Source[AudFrame] {
    override protected def produce(time: Long, track: Track, segment: Segment): AudFrame = {
      if (frame == null) {
        frame = new AudFrame(48000, -1)
        frame.samples = new Array[Float](BASELINE.length)
      }
      System.arraycopy(BASELINE, 0, frame.samples, 0, BASELINE.length)
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
      "测试音频源"
    }

    override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
      null
    }
  }
}
