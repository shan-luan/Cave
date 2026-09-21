package com.lomekwi.cave.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnitsTest {

  @Test
  def timebaseIsOneMicrosecond(): Unit = {
    assertEquals(1_000_000L, Units.SECOND)
    assertEquals(24L * 60L * 60L * Units.SECOND, Units.DAY)
  }

  @Test
  def niceScale_roundsToNiceIntegers(): Unit = {
    assertEquals(1L, Units.niceScale(1))
    assertEquals(2L, Units.niceScale(2))
    assertEquals(2L, Units.niceScale(3))
    assertEquals(5L, Units.niceScale(5))
    assertEquals(5L, Units.niceScale(9))
    assertEquals(10L, Units.niceScale(10))
    assertEquals(20L, Units.niceScale(25))
    assertEquals(50L, Units.niceScale(99))
    assertEquals(1000L, Units.niceScale(1234))
  }

  @Test
  def niceScale_staysInTimebase(): Unit = {
    assertEquals(10L * Units.SECOND, Units.niceScale(12L * Units.SECOND))
  }

  @Test
  def niceScale_nonPositiveReturnsZero(): Unit = {
    assertEquals(0L, Units.niceScale(0))
    assertEquals(0L, Units.niceScale(-5))
  }

  @Test
  def niceInterval_roundsToNiceFloats(): Unit = {
    assertEquals(1f, Units.niceInterval(1f), 1e-6f)
    assertEquals(2f, Units.niceInterval(2f), 1e-6f)
    assertEquals(2f, Units.niceInterval(3f), 1e-6f)
    assertEquals(5f, Units.niceInterval(5f), 1e-6f)
    assertEquals(5f, Units.niceInterval(7f), 1e-6f)
    assertEquals(10f, Units.niceInterval(10f), 1e-6f)
    assertEquals(20f, Units.niceInterval(20f), 1e-6f)
    assertEquals(50f, Units.niceInterval(50f), 1e-6f)
    assertEquals(100f, Units.niceInterval(100f), 1e-6f)
  }

  @Test
  def niceInterval_nonPositiveClampsToEpsilon(): Unit = {
    assertEquals(1e-10f, Units.niceInterval(0f), 1e-20f)
    assertEquals(1e-10f, Units.niceInterval(-3f), 1e-20f)
  }
}
