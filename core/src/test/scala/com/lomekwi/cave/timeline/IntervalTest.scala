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
