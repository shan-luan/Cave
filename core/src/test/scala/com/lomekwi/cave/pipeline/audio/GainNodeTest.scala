package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.timeline.{Boundless, Segment, Track}
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

import GainNodeTest.*

/**
 * 验证 [[GainNode]] 沿滤镜链把音量乘到采样点上。
 */
class GainNodeTest extends munit.FunSuite {

  test("默认增益不改变采样") {
    val segment = new AudCont
    segment.attach(new GainNode())

    assertSamples(segment, BASELINE)
  }

  test("增益按比例缩放采样") {
    val segment = new AudCont
    val node = new GainNode()
    node.setGain(0.5)
    segment.attach(node)

    assertSamples(segment, Array(0.05f, -0.1f, 0.25f))
  }

  test("增益不做钳制") {
    val segment = new AudCont
    val node = new GainNode()
    node.setGain(3.0)
    segment.attach(node)

    assertSamples(segment, Array(0.3f, -0.6f, 1.5f))
  }

  test("串联节点叠加增益") {
    val segment = new AudCont
    val a = new GainNode()
    a.setGain(2.0)
    val b = new GainNode()
    b.setGain(0.25)
    segment.attach(a)
    segment.attach(b)

    assertSamples(segment, Array(0.05f, -0.1f, 0.25f))
  }

  test("重复求值结果稳定") {
    val segment = new AudCont
    val node = new GainNode()
    node.setGain(2.0)
    segment.attach(node)

    assertSamples(segment, Array(0.2f, -0.4f, 1.0f))
    assertSamples(segment, Array(0.2f, -0.4f, 1.0f))
    assertSamples(segment, Array(0.2f, -0.4f, 1.0f))
  }

  private def assertSamples(segment: Segment, expected: Array[Float]): Unit = {
    val frame = segment.get(0, null).asInstanceOf[AudFrame]
    assertEquals(frame.samples.length, expected.length)
    for (i <- expected.indices) {
      assertEqualsFloat(frame.samples(i), expected(i), 1e-6f)
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
