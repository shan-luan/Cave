package com.lomekwi.cave.pipeline.image

import org.junit.jupiter.api.Assertions.{assertEquals, assertNull, assertSame, assertTrue}
import org.junit.jupiter.api.Test

class CrossfadeSourceTest {

  @Test
  def mixFadesInOverFullyVisibleFrom(): Unit = {
    val source = new CrossfadeSource(null, null)
    val a = new ImgFrame(0)
    val b = new ImgFrame(0)

    val result = source.mix(a, b, 0.25f)

    assertTrue(result.isInstanceOf[BiRenderFrame])
    assertSame(a, result.a)
    assertSame(b, result.b)
    // 前帧保持全显，只有后帧渐显，over 合成才是线性叠化
    assertEquals(1f, a.opacity, 1e-6f)
    assertEquals(0.25f, b.opacity, 1e-6f)
    assertEquals(0, result.trackIndex)
  }

  @Test
  def mixToleratesMissingFromSide(): Unit = {
    val source = new CrossfadeSource(null, null)
    val b = new ImgFrame(0)

    val result = source.mix(null, b, 0.5f)

    assertNull(result.a)
    assertSame(b, result.b)
    assertEquals(0.5f, b.opacity, 1e-6f)
    assertEquals(0, result.trackIndex)
  }

  @Test
  def mixToleratesMissingToSide(): Unit = {
    val source = new CrossfadeSource(null, null)
    val a = new ImgFrame(3)

    val result = source.mix(a, null, 0.5f)

    assertSame(a, result.a)
    assertNull(result.b)
    assertEquals(1f, a.opacity, 1e-6f)
    assertEquals(3, result.trackIndex)
  }

  @Test
  def mixReusesCachedFrame(): Unit = {
    val source = new CrossfadeSource(null, null)
    val a1 = new ImgFrame(0)
    val b1 = new ImgFrame(0)
    val first = source.mix(a1, b1, 0.25f)

    val a2 = new ImgFrame(0)
    val b2 = new ImgFrame(0)
    val second = source.mix(a2, b2, 0.75f)

    assertSame(first, second)
    assertSame(a2, second.a)
    assertSame(b2, second.b)
    assertEquals(0.75f, b2.opacity, 1e-6f)
  }
}
