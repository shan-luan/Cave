package com.lomekwi.cave.timeline

import com.lomekwi.cave.app.AppAudioOut
import com.lomekwi.cave.pipeline.{Clip, Segment, Source, Transition, TransitionSource}
import com.lomekwi.cave.pipeline.audio.{AudCrossfadeSource, AudFrame}
import com.lomekwi.cave.project.TestProject
import com.lomekwi.cave.resource.decoder.AudDecRes
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue, fail}
import org.junit.jupiter.api.{BeforeEach, Test}

/**
 * 音频转场在轨道上的派生测试。两个音频片段重叠时应自动生成声音叠化，并产出正确混合的帧。
 */
class AudTransitionTrackTest extends GdxTestBase {

  private var timeline: Timeline = null

  @BeforeEach
  def setUp(): Unit = {
    timeline = new TestProject().timeline
  }

  /**
   * 产出固定填充值帧的音频探针源。转场接线复刻 [[com.lomekwi.cave.pipeline.audio.AudSource]]，
   * 探针无法持有需要真实文件的 AudRes。
   */
  private class AudProbeSource(fill: Float) extends Source[AudFrame] {
    override protected def produce(time: Long, track: Track, segment: Segment): AudFrame = {
      if (frame == null || frame.trackIndex != track.index) {
        frame = new AudFrame(AppAudioOut.SAMPLE_RATE, track.index)
      }
      frame.samples = Array.fill(AudDecRes.FRAME_SIZE)(fill)
      frame
    }

    override def getLengthPerExportFrame: Long = 1

    override def getDuration: Long = 1000L

    override def getDefaultDuration: Option[Long] = Some(1000L)

    override def displayName: String = "aud-probe"

    override def createTlSegmentActor(segment: Segment): TlSegmentActor = null

    override def canCreateTransitionWith(source: Source[?]): Boolean = {
      classOf[AudFrame].isAssignableFrom(source.getType)
    }

    override def createTransition(source: Source[?]): TransitionSource[AudFrame, AudFrame] = {
      new AudCrossfadeSource(this, source.asInstanceOf[Source[AudFrame]])
    }
  }

  @Test
  def overlappingAudSegmentsDeriveCrossfade(): Unit = {
    val track = timeline.getTrackOrCreate(0)
    timeline.addOrThrow(track, new Clip(new AudProbeSource(0.5f)), 0L ~~ 1000L, 0L)
    timeline.addOrThrow(track, new Clip(new AudProbeSource(-0.25f)), 500L ~~ 1500L, 500L)
    val current = timeline.getTrackOrCreate(0)

    val transition = current.get(600L) match {
      case t: Transition => t
      case other => fail("重叠处应是转场，实际: " + other)
    }
    assertTrue(transition.source.isInstanceOf[AudCrossfadeSource])

    // 转场区间 [500, 1000)，t=600 的局部进度为 0.2
    val mixed = current.frameAt(transition, 600L).asInstanceOf[AudFrame]
    val expected = 0.5f * math.cos(0.2f * math.Pi / 2).toFloat - 0.25f * math.sin(0.2f * math.Pi / 2).toFloat
    assertEquals(expected, mixed.samples(0), 1e-5f)
  }
}
