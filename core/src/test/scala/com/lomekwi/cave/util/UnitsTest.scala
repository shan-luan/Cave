package com.lomekwi.cave.util

class UnitsTest extends munit.FunSuite {

  test("时间基为一微秒") {
    assertEquals(Units.SECOND, 1_000_000L)
    assertEquals(Units.DAY, 24L * 60L * 60L * Units.SECOND)
  }

  test("niceScale 取整到易读整数") {
    assertEquals(Units.niceScale(1), 1L)
    assertEquals(Units.niceScale(2), 2L)
    assertEquals(Units.niceScale(3), 2L)
    assertEquals(Units.niceScale(5), 5L)
    assertEquals(Units.niceScale(9), 5L)
    assertEquals(Units.niceScale(10), 10L)
    assertEquals(Units.niceScale(25), 20L)
    assertEquals(Units.niceScale(99), 50L)
    assertEquals(Units.niceScale(1234), 1000L)
  }

  test("niceScale 结果保持时间基粒度") {
    assertEquals(Units.niceScale(12L * Units.SECOND), 10L * Units.SECOND)
  }

  test("niceScale 对非正数返回零") {
    assertEquals(Units.niceScale(0), 0L)
    assertEquals(Units.niceScale(-5), 0L)
  }

  test("niceInterval 取整到易读浮点数") {
    assertEqualsFloat(Units.niceInterval(1f), 1f, 1e-6f)
    assertEqualsFloat(Units.niceInterval(2f), 2f, 1e-6f)
    assertEqualsFloat(Units.niceInterval(3f), 2f, 1e-6f)
    assertEqualsFloat(Units.niceInterval(5f), 5f, 1e-6f)
    assertEqualsFloat(Units.niceInterval(7f), 5f, 1e-6f)
    assertEqualsFloat(Units.niceInterval(10f), 10f, 1e-6f)
    assertEqualsFloat(Units.niceInterval(20f), 20f, 1e-6f)
    assertEqualsFloat(Units.niceInterval(50f), 50f, 1e-6f)
    assertEqualsFloat(Units.niceInterval(100f), 100f, 1e-6f)
  }

  test("niceInterval 对非正数钳制到极小值") {
    assertEqualsFloat(Units.niceInterval(0f), 1e-10f, 1e-20f)
    assertEqualsFloat(Units.niceInterval(-3f), 1e-10f, 1e-20f)
  }
}
