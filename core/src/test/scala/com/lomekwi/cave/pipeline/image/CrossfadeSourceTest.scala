package com.lomekwi.cave.pipeline.image

import org.junit.jupiter.api.Assertions.{assertEquals, assertNull, assertSame, assertTrue}
import org.junit.jupiter.api.Test

class CrossfadeSourceTest {

  private val source = new CrossfadeSource(null, null)

  @Test
  def mixFadesLinearly(): Unit = {
    val a = new ImgFrame(0)
    val b = new ImgFrame(0)

    val result = source.mix(a, b, 0.25f)

    assertTrue(result.isInstanceOf[BiRenderFrame])
    assertSame(a, result.a)
    assertSame(b, result.b)
    assertEquals(0.75f, a.opacity, 1e-6f)
    assertEquals(0.25f, b.opacity, 1e-6f)
    assertEquals(0, result.trackIndex)
  }

  @Test
  def mixToleratesMissingFromSide(): Unit = {
    val b = new ImgFrame(0)

    val result = source.mix(null, b, 0.5f)

    assertNull(result.a)
    assertSame(b, result.b)
    assertEquals(0.5f, b.opacity, 1e-6f)
    assertEquals(0, result.trackIndex)
  }

  @Test
  def mixToleratesMissingToSide(): Unit = {
    val a = new ImgFrame(3)

    val result = source.mix(a, null, 0.5f)

    assertSame(a, result.a)
    assertNull(result.b)
    assertEquals(0.5f, a.opacity, 1e-6f)
    assertEquals(3, result.trackIndex)
  }
}
