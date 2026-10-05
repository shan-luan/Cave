package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.app.AppAudioOut
import com.lomekwi.cave.resource.decoder.AudDecRes

import org.junit.jupiter.api.Assertions.{assertEquals, assertNotSame, assertSame, assertTrue}
import org.junit.jupiter.api.Test

/**
 * 声音叠化转场混合行为的测试。轨道上的转场派生在 timeline 包的 AudTransitionTrackTest 覆盖。
 */
class AudCrossfadeSourceTest {

  private def audFrame(trackIndex: Int, fill: Float): AudFrame = {
    val f = new AudFrame(AppAudioOut.SAMPLE_RATE, trackIndex)
    f.samples = Array.fill(AudDecRes.FRAME_SIZE)(fill)
    f
  }

  @Test
  def mixBlendsSamplesWithEqualPowerWeights(): Unit = {
    val source = new AudCrossfadeSource(null, null)
    val a = audFrame(0, 0.5f)
    val b = audFrame(0, -0.25f)

    val atZero = source.mix(a, b, 0f)
    assertEquals(0.5f, atZero.samples(0), 1e-6f)

    val atQuarter = source.mix(a, b, 0.25f)
    val quarterExpected = 0.5f * math.cos(0.25f * math.Pi / 2).toFloat - 0.25f * math.sin(0.25f * math.Pi / 2).toFloat
    assertEquals(quarterExpected, atQuarter.samples(0), 1e-5f)
    assertEquals(quarterExpected, atQuarter.samples(AudDecRes.FRAME_SIZE - 1), 1e-5f)

    // 中点两路权重各为 √2/2
    val atHalf = source.mix(a, b, 0.5f)
    assertEquals((0.5f - 0.25f) * math.sqrt(2f).toFloat / 2f, atHalf.samples(0), 1e-5f)
  }

  @Test
  def mixPassesThroughTheSideWithFrame(): Unit = {
    val source = new AudCrossfadeSource(null, null)
    val a = audFrame(0, 0.5f)
    val b = audFrame(0, -0.25f)

    assertSame(a, source.mix(a, null, 0.5f))
    assertSame(b, source.mix(null, b, 0.5f))
  }

  @Test
  def mixReusesCachedFrameWithIndependentSamples(): Unit = {
    val source = new AudCrossfadeSource(null, null)
    val a1 = audFrame(0, 0.5f)
    val b1 = audFrame(0, -0.25f)
    val first = source.mix(a1, b1, 0.25f)

    assertNotSame(first.samples, a1.samples)
    assertNotSame(first.samples, b1.samples)

    val a2 = audFrame(0, 0.5f)
    val b2 = audFrame(0, -0.25f)
    val second = source.mix(a2, b2, 0.75f)

    assertSame(first, second)
    assertSame(first.samples, second.samples)
  }

  @Test
  def mixRebuildsFrameWhenTrackIndexChanges(): Unit = {
    val source = new AudCrossfadeSource(null, null)
    val first = source.mix(audFrame(0, 0.5f), audFrame(0, -0.25f), 0.5f)
    val second = source.mix(audFrame(2, 0.5f), audFrame(2, -0.25f), 0.5f)

    assertNotSame(first, second)
    assertEquals(2, second.trackIndex)
  }

  @Test
  def audSourceWiresCrossfadeTransition(): Unit = {
    val source = new AudSource(null)

    assertTrue(source.canCreateTransitionWith(new AudSource(null)))

    val transition = source.createTransition(new AudSource(null))
    assertTrue(transition.isInstanceOf[AudCrossfadeSource])
  }
}
