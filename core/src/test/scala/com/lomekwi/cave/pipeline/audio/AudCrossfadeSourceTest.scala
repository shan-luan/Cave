package com.lomekwi.cave.pipeline.audio

import com.lomekwi.cave.app.AppAudioOut
import com.lomekwi.cave.resource.decoder.AudDecRes

/**
 * 声音叠化转场混合行为的测试。轨道上的转场派生在 timeline 包的 AudTransitionTrackTest 覆盖。
 */
class AudCrossfadeSourceTest extends munit.FunSuite {

  private def audFrame(trackIndex: Int, fill: Float): AudFrame = {
    val f = new AudFrame(AppAudioOut.SAMPLE_RATE, trackIndex)
    f.samples = Array.fill(AudDecRes.FRAME_SIZE)(fill)
    f
  }

  test("mix 用等功率权重混合采样") {
    val source = new AudCrossfadeSource(null, null)
    val a = audFrame(0, 0.5f)
    val b = audFrame(0, -0.25f)

    val atZero = source.mix(a, b, 0f)
    assertEqualsFloat(atZero.samples(0), 0.5f, 1e-6f)

    val atQuarter = source.mix(a, b, 0.25f)
    val quarterExpected = 0.5f * math.cos(0.25f * math.Pi / 2).toFloat - 0.25f * math.sin(0.25f * math.Pi / 2).toFloat
    assertEqualsFloat(atQuarter.samples(0), quarterExpected, 1e-5f)
    assertEqualsFloat(atQuarter.samples(AudDecRes.FRAME_SIZE - 1), quarterExpected, 1e-5f)

    // 中点两路权重各为 √2/2
    val atHalf = source.mix(a, b, 0.5f)
    assertEqualsFloat(atHalf.samples(0), (0.5f - 0.25f) * math.sqrt(2f).toFloat / 2f, 1e-5f)
  }

  test("只有一侧有帧时 mix 直接透传") {
    val source = new AudCrossfadeSource(null, null)
    val a = audFrame(0, 0.5f)
    val b = audFrame(0, -0.25f)

    assert((a) eq (source.mix(a, null, 0.5f)))
    assert((b) eq (source.mix(null, b, 0.5f)))
  }

  test("mix 复用缓存帧但采样独立") {
    val source = new AudCrossfadeSource(null, null)
    val a1 = audFrame(0, 0.5f)
    val b1 = audFrame(0, -0.25f)
    val first = source.mix(a1, b1, 0.25f)

    assert(!((first.samples) eq (a1.samples)))
    assert(!((first.samples) eq (b1.samples)))

    val a2 = audFrame(0, 0.5f)
    val b2 = audFrame(0, -0.25f)
    val second = source.mix(a2, b2, 0.75f)

    assert((first) eq (second))
    assert((first.samples) eq (second.samples))
  }

  test("轨道索引变化时 mix 重建帧") {
    val source = new AudCrossfadeSource(null, null)
    val first = source.mix(audFrame(0, 0.5f), audFrame(0, -0.25f), 0.5f)
    val second = source.mix(audFrame(2, 0.5f), audFrame(2, -0.25f), 0.5f)

    assert(!((first) eq (second)))
    assertEquals(second.trackIndex, 2)
  }

  test("音频源接入交叉淡化转场") {
    val source = new AudSource(null)

    assert(source.canCreateTransitionWith(new AudSource(null)))

    val transition = source.createTransition(new AudSource(null))
    assert(transition.isInstanceOf[AudCrossfadeSource])
  }
}
