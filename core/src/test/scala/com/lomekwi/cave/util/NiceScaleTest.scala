package com.lomekwi.cave.util

class NiceScaleTest extends munit.FunSuite {

  test("niceScale 取整到易读整数") {
    assertEquals(niceScale(1), 1L)
    assertEquals(niceScale(2), 2L)
    assertEquals(niceScale(3), 2L)
    assertEquals(niceScale(5), 5L)
    assertEquals(niceScale(9), 5L)
    assertEquals(niceScale(10), 10L)
    assertEquals(niceScale(25), 20L)
    assertEquals(niceScale(99), 50L)
    assertEquals(niceScale(1234), 1000L)
  }

  test("niceScale 结果保持时间基粒度") {
    assertEquals(niceScale(12_000_000L), 10_000_000L)
  }

  test("niceScale 对非正数返回零") {
    assertEquals(niceScale(0), 0L)
    assertEquals(niceScale(-5), 0L)
  }

  test("niceInterval 取整到易读浮点数") {
    assertEqualsFloat(niceInterval(1f), 1f, 1e-6f)
    assertEqualsFloat(niceInterval(2f), 2f, 1e-6f)
    assertEqualsFloat(niceInterval(3f), 2f, 1e-6f)
    assertEqualsFloat(niceInterval(5f), 5f, 1e-6f)
    assertEqualsFloat(niceInterval(7f), 5f, 1e-6f)
    assertEqualsFloat(niceInterval(10f), 10f, 1e-6f)
    assertEqualsFloat(niceInterval(20f), 20f, 1e-6f)
    assertEqualsFloat(niceInterval(50f), 50f, 1e-6f)
    assertEqualsFloat(niceInterval(100f), 100f, 1e-6f)
  }

  test("niceInterval 对非正数钳制到极小值") {
    assertEqualsFloat(niceInterval(0f), 1e-10f, 1e-20f)
    assertEqualsFloat(niceInterval(-3f), 1e-10f, 1e-20f)
  }
}
