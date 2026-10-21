package com.lomekwi.cave.pipeline.image

class CrossfadeSourceTest extends munit.FunSuite {

  test("在完全可见的 from 侧上淡入") {
    val source = new CrossfadeSource(null, null)
    val a = new ImgFrame(0)
    val b = new ImgFrame(0)

    val result = source.mix(a, b, 0.25f)

    assert(result.isInstanceOf[BiRenderFrame])
    assert((a) eq (result.a))
    assert((b) eq (result.b))
    assertEqualsFloat(a.opacity, 1f, 1e-6f)
    assertEqualsFloat(b.opacity, 0.25f, 1e-6f)
    assertEquals(result.trackIndex, 0)
  }

  test("from 侧缺失时 mix 仍可用") {
    val source = new CrossfadeSource(null, null)
    val b = new ImgFrame(0)

    val result = source.mix(null, b, 0.5f)

    assert((result.a) == null)
    assert((b) eq (result.b))
    assertEqualsFloat(b.opacity, 0.5f, 1e-6f)
    assertEquals(result.trackIndex, 0)
  }

  test("to 侧缺失时 mix 仍可用") {
    val source = new CrossfadeSource(null, null)
    val a = new ImgFrame(3)

    val result = source.mix(a, null, 0.5f)

    assert((a) eq (result.a))
    assert((result.b) == null)
    assertEqualsFloat(a.opacity, 1f, 1e-6f)
    assertEquals(result.trackIndex, 3)
  }

  test("mix 复用缓存帧") {
    val source = new CrossfadeSource(null, null)
    val a1 = new ImgFrame(0)
    val b1 = new ImgFrame(0)
    val first = source.mix(a1, b1, 0.25f)

    val a2 = new ImgFrame(0)
    val b2 = new ImgFrame(0)
    val second = source.mix(a2, b2, 0.75f)

    assert((first) eq (second))
    assert((a2) eq (second.a))
    assert((b2) eq (second.b))
    assertEqualsFloat(b2.opacity, 0.75f, 1e-6f)
  }

  test("每次产出时 mix 重置自身透明度") {
    val source = new CrossfadeSource(null, null)
    val a = new ImgFrame(0)
    val b = new ImgFrame(0)

    val first = source.mix(a, b, 0.25f)
    first.opacity = 0.3f

    val second = source.mix(a, b, 0.5f)

    assertEqualsFloat(second.opacity, 1f, 1e-6f)
  }
}
