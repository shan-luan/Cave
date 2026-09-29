package com.lomekwi.cave.timeline

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test

class IntervalTest {

  @Test
  def contains_excludesUpperEndpoint(): Unit = {
    val r = 100 ~~ 200
    assertTrue(r.contains(100))
    assertTrue(r.contains(199))
    assertFalse(r.contains(200))
    assertFalse(r.contains(99))
  }

  @Test
  def emptyInterval_containsNothing(): Unit = {
    val r = 100 ~~ 100
    assertTrue(r.isEmpty)
    assertFalse(r.contains(100))
  }

  @Test
  def intersects_requiresCommonPoint(): Unit = {
    assertTrue((0 ~~ 10).intersects(5 ~~ 15))
    assertFalse((0 ~~ 10).intersects(10 ~~ 20))
  }

  @Test
  def and_returnsCommonPart(): Unit = {
    assertEquals(5 ~~ 10, (0 ~~ 10) & (5 ~~ 15))
    assertEquals(0 ~~ 10, (0 ~~ 10) & (-5 ~~ 10))
    assertEquals(2 ~~ 8, (0 ~~ 10) & (2 ~~ 8))
    assertEquals((0 ~~ 10) & (2 ~~ 8), (2 ~~ 8) & (0 ~~ 10))
  }

  @Test
  def and_isEmptyWhenDisjointOrTouching(): Unit = {
    assertEquals(10 ~~ 10, (0 ~~ 10) & (10 ~~ 20))
    assertEquals(20 ~~ 20, (0 ~~ 10) & (20 ~~ 30))
    assertEquals(20 ~~ 20, (20 ~~ 30) & (0 ~~ 10))
    assertTrue(((0 ~~ 10) & (20 ~~ 30)).isEmpty)
    assertFalse(((0 ~~ 10) & (9 ~~ 30)).isEmpty)
  }

  @Test
  def and_withEmptyIntervalIsEmpty(): Unit = {
    assertTrue(((0 ~~ 10) & (5 ~~ 5)).isEmpty)
    assertTrue(((5 ~~ 5) & (0 ~~ 10)).isEmpty)
    assertTrue(((0 ~~ 0) & (0 ~~ 0)).isEmpty)
  }

  @Test
  def isConnected_countsAdjacent(): Unit = {
    assertTrue((0 ~~ 10).isConnected(5 ~~ 15))
    assertTrue((0 ~~ 10).isConnected(10 ~~ 20))
    assertFalse((0 ~~ 10).isConnected(11 ~~ 20))
  }

  @Test
  def shift_movesBothEndpoints(): Unit = {
    assertEquals(110 ~~ 210, (100 ~~ 200).shift(10))
    assertEquals(90 ~~ 190, (100 ~~ 200).shift(-10))
  }

  @Test
  def ordering_isLexicographic(): Unit = {
    val sorted = List(10 ~~ 20, 0 ~~ 5, 0 ~~ 3).sorted
    assertEquals(List(0 ~~ 3, 0 ~~ 5, 10 ~~ 20), sorted)
  }
}
