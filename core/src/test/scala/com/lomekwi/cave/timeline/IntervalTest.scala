package com.lomekwi.cave.timeline

import com.lomekwi.cave.timeline.Interval.~

import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.Test

class IntervalTest {

  @Test
  def contains_excludesUpperEndpoint(): Unit = {
    val r = Interval(100, 200)
    assertTrue(r.contains(100))
    assertTrue(r.contains(199))
    assertFalse(r.contains(200))
    assertFalse(r.contains(99))
  }

  @Test
  def emptyInterval_containsNothing(): Unit = {
    val r = Interval(100, 100)
    assertTrue(r.isEmpty)
    assertFalse(r.contains(100))
  }

  @Test
  def intersects_requiresCommonPoint(): Unit = {
    assertTrue(Interval(0, 10).intersects(Interval(5, 15)))
    assertFalse(Interval(0, 10).intersects(Interval(10, 20)))
  }

  @Test
  def isConnected_countsAdjacent(): Unit = {
    assertTrue(Interval(0, 10).isConnected(Interval(5, 15)))
    assertTrue(Interval(0, 10).isConnected(Interval(10, 20)))
    assertFalse(Interval(0, 10).isConnected(Interval(11, 20)))
  }

  @Test
  def shift_movesBothEndpoints(): Unit = {
    assertEquals(Interval(110, 210), Interval(100, 200).shift(10))
    assertEquals(Interval(90, 190), Interval(100, 200).shift(-10))
  }

  @Test
  def infixOperator_buildsInterval(): Unit = {
    assertEquals(Interval(1, 100), 1 ~ 100)
    assertEquals(Interval(1, 100), 1L ~ 100L)
  }

  @Test
  def infixOperator_needsParensAroundComputedEndpoint(): Unit = {
    val time = 10L
    val duration = 100L
    assertEquals(Interval(10, 110), time ~ (time + duration))
  }

  @Test
  def ordering_isLexicographic(): Unit = {
    val sorted = List(Interval(10, 20), Interval(0, 5), Interval(0, 3)).sorted
    assertEquals(List(Interval(0, 3), Interval(0, 5), Interval(10, 20)), sorted)
  }
}
