package com.lomekwi.cave.timeline

import com.lomekwi.cave.app.AppAudioOut
import com.lomekwi.cave.pipeline.{Source, TransitionSource}
import com.lomekwi.cave.pipeline.audio.{AudCrossfadeSource, AudFrame}
import com.lomekwi.cave.project.{TestIds, TestProject}
import com.lomekwi.cave.resource.decoder.AudDecRes
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

/**
 * 音频转场在轨道上的派生测试。
 */
class AudTransitionTrackTest extends GdxTestBase {

  private var timeline: TestTimeline = null

  private def at(value: Long): Instant = Instant.At(Timespan(value))

  override def beforeEach(context: BeforeEach): Unit = {
    timeline = new TestProject().timeline
  }

  /** 产出固定填充值帧的音频探针源。 */
  private class AudProbeSource(fill: Float) extends Source[AudFrame] {
    override protected def produce(time: Timespan, track: Track, segment: Segment): AudFrame = {
      if (frame == null || frame.trackIndex != track.index) {
        frame = new AudFrame(AppAudioOut.SAMPLE_RATE, track.index)
      }
      frame.samples = Array.fill(AudDecRes.FRAME_SIZE)(fill)
      frame
    }

    override def getLengthPerExportFrame: Timespan = Timespan(1)

    override def getDuration: Timespan = Timespan(1000)

    override def getDefaultDuration: Option[Timespan] = Some(Timespan(1000))

    override def displayName: String = "aud-probe"

    override def createTlSegmentActor(segment: Segment): TlSegmentActor = null

    override def canCreateTransitionWith(source: Source[?]): Boolean = {
      classOf[AudFrame].isAssignableFrom(source.getType)
    }

    override def createTransition(source: Source[?]): TransitionSource[AudFrame, AudFrame] = {
      new AudCrossfadeSource(this, source.asInstanceOf[Source[AudFrame]])
    }
  }

  test("重叠的音频片段推导出交叉淡化转场") {
    val track = timeline.getTrackOrCreate(0)
    timeline.add(track, new Clip(TestIds.nextId(), new AudProbeSource(0.5f)), at(0) ~~ at(1000), Instant.Zero)
    timeline.add(track, new Clip(TestIds.nextId(), new AudProbeSource(-0.25f)), at(500) ~~ at(1500), at(500))
    val current = timeline.getTrackOrCreate(0)

    val transition = current.get(at(600)) match {
      case t: Transition => t
      case other => fail("重叠处应是转场，实际: " + other)
    }
    assert(transition.source.isInstanceOf[AudCrossfadeSource])

    val mixed = current.frameAt(transition, at(600)).asInstanceOf[AudFrame]
    val expected = 0.5f * math.cos(0.2f * math.Pi / 2).toFloat - 0.25f * math.sin(0.2f * math.Pi / 2).toFloat
    assertEqualsFloat(mixed.samples(0), expected, 1e-5f)
  }
}
