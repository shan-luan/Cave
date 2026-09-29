package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Content, Gap, Segment, Transition}
import com.lomekwi.cave.project.TestProject

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.util.List

import scala.jdk.CollectionConverters.*

/**
 * 删除路径的场景测试。内容与转场的删除在各种邻居与边界形态下的行为；
 * 三条不变量校验移除后，这些性质由本测试守护。
 */
class TrackRemoveTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: Timeline = null

  @BeforeEach
  def setUp(): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  private def newContent(duration: Long): Content[?] = new TestCont(duration)

  private def place(track: Track, segment: Segment[?], range: Interval, origin: Long = 0L): Unit = {
    timeline.addOrThrow(track, segment, range, origin)
  }

  private def transitionOf(track: Track, time: Long): Transition[?] = track.get(time) match {
    case t: Transition[?] => t
    case _ => null
  }

  private def contentsOf(track: Track): Vector[Content[?]] =
    track.asScala.collect { case c: Content[?] => c }.toVector

  /** 相邻内容交叉、隔项不重叠、转场区间恒等于重叠区。 */
  private def assertLayoutValid(track: Track): Unit = {
    val contents = contentsOf(track)
    var i = 0
    while (i < contents.size) {
      val cr = track.getRange(contents(i))
      if (i + 1 < contents.size) {
        val nr = track.getRange(contents(i + 1))
        if (cr.hi > nr.lo) {
          assertTrue(cr.lo < nr.lo && cr.hi < nr.hi, "相邻内容未交叉: " + cr + " " + nr)
        }
      }
      if (i + 2 < contents.size) {
        val nn = track.getRange(contents(i + 2))
        assertFalse(cr.hi > nn.lo, "隔项重叠: " + cr + " " + nn)
      }
      i += 1
    }
    for (s <- track.asScala) {
      s match {
        case t: Transition[?] =>
          val sides = track.transitionSides(t)
          assertNotNull(sides, "转场两侧缺失")
          if (sides != null) {
            val lo = track.getRange(sides._2).lo
            val hi = track.getRange(sides._1).hi
            assertTrue(lo < hi, "转场两侧没有重叠")
            assertEquals(lo ~~ hi, track.getRange(t), "转场区间不是重叠区")
          }
        case _ =>
      }
    }
  }

  @Test
  def removingFirstContentDetachesEverythingAfterIt(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    assertNotNull(transitionOf(t0, 700))

    timeline.remove(a)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(500 ~~ 1500, after.getRange(b))
    assertNull(transitionOf(after, 700))
    assertTrue(after.get(300).isInstanceOf[Gap])
    assertLayoutValid(after)
  }

  @Test
  def removingLastContentShortensTrackLength(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    timeline.remove(b)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(0 ~~ 1000, after.getRange(a))
    assertEquals(1000L, after.length)
    assertNull(transitionOf(after, 700))
    assertTrue(after.get(1200).isInstanceOf[Gap])
    assertLayoutValid(after)
  }

  @Test
  def removingOnlyContentLeavesEmptyTrack(): Unit = {
    val t0 = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    place(t0, a, 0 ~~ 1000)

    timeline.remove(a)

    val after = timeline.getTrackOrCreate(0)
    assertTrue(after.isEmpty)
    assertEquals(0L, after.length)
    assertTrue(after.asScala.isEmpty)
  }

  @Test
  def removingMiddleContentDetachesBothNeighbours(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    val c = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    place(t0, c, 1000 ~~ 2000)
    assertNotNull(transitionOf(t0, 700))
    assertNotNull(transitionOf(t0, 1200))

    timeline.remove(b)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(0 ~~ 1000, after.getRange(a))
    assertEquals(1000 ~~ 2000, after.getRange(c))
    assertSame(a, after.get(700))
    assertSame(c, after.get(1200))
    assertNull(after.transitionBetween(a, c))
    assertLayoutValid(after)
  }

  @Test
  def removingForeignSegmentIsNoOp(): Unit = {
    val t0 = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val foreign = newContent(1000)
    place(t0, a, 0 ~~ 1000)

    val current = timeline.getTrackOrCreate(0)
    assertSame(current, current.remove(foreign))
  }

  @Test
  def removingContentKeepsUnrelatedTransitionIdentity(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    val c = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    place(t0, c, 2000 ~~ 3000)
    val transition = transitionOf(t0, 700)
    assertNotNull(transition)

    timeline.remove(c)

    val after = timeline.getTrackOrCreate(0)
    assertSame(transition, transitionOf(after, 700))
    assertLayoutValid(after)
  }

  @Test
  def removingContentLeavesGapReusable(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 2000 ~~ 3000)

    timeline.remove(a)
    val c = newContent(1000)
    place(timeline.getTrackOrCreate(0), c, 500 ~~ 1500)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(500 ~~ 1500, after.getRange(c))
    assertTrue(after.get(100).isInstanceOf[Gap])
    assertLayoutValid(after)
  }

  @Test
  def removingContentsInBatchDetachesEachFromTrack(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    val c = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    place(t0, c, 2000 ~~ 3000)

    timeline.remove(List.of(a, c))

    val after = timeline.getTrackOrCreate(0)
    val remaining = contentsOf(after)
    assertEquals(1, remaining.size)
    assertSame(b, remaining.head)
    assertEquals(500 ~~ 1500, after.getRange(b))
    assertTrue(after.get(2500).isInstanceOf[Gap])
    assertLayoutValid(after)
  }

  @Test
  def removingTransitionWithOddOverlapSplitsAtFloorHalf(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 989 ~~ 1500)
    val transition = transitionOf(t0, 995)
    assertNotNull(transition)
    assertEquals(989 ~~ 1000, t0.getRange(transition))

    timeline.remove(transition)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(0 ~~ 995, after.getRange(a))
    assertEquals(995 ~~ 1500, after.getRange(b))
    assertNull(after.transitionBetween(a, b))
    assertLayoutValid(after)
  }

  @Test
  def removingMinimalOverlapTransitionMovesRightToLeftEnd(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 999 ~~ 1500)
    val transition = transitionOf(t0, 999)
    assertNotNull(transition)
    assertEquals(999 ~~ 1000, t0.getRange(transition))

    timeline.remove(transition)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(0 ~~ 1000, after.getRange(a))
    assertEquals(1000 ~~ 1500, after.getRange(b))
    assertNull(after.transitionBetween(a, b))
    assertLayoutValid(after)
  }

  @Test
  def removingTransitionLeavesLeftNeighbourTransitionIntact(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val p = newContent(1000)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, p, 0 ~~ 999)
    place(t0, a, 100 ~~ 1000)
    place(t0, b, 999 ~~ 1100)
    val transition = transitionOf(t0, 999)
    assertNotNull(transition)

    timeline.remove(transition)

    // a 的终点不能退到 p 的终点之前，否则 p 会把它包含；交界点停在 a 的原终点上，a 不让路
    val after = timeline.getTrackOrCreate(0)
    assertEquals(100 ~~ 1000, after.getRange(a))
    assertEquals(1000 ~~ 1100, after.getRange(b))
    assertNotNull(transitionOf(after, 500))
    assertLayoutValid(after)
  }

  @Test
  def removingTransitionStopsBeforeSecondSuccessor(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    val rn = newContent(1000)
    place(t0, a, 1000 ~~ 1101)
    place(t0, b, 1100 ~~ 1500)
    place(t0, rn, 1101 ~~ 2000)
    val transition = transitionOf(t0, 1100)
    assertNotNull(transition)

    timeline.remove(transition)

    // 交界点不能越过 b 的右邻起点，a 退到它的终点就停
    val after = timeline.getTrackOrCreate(0)
    assertEquals(1000 ~~ 1100, after.getRange(a))
    assertEquals(1100 ~~ 1500, after.getRange(b))
    assertEquals(1101 ~~ 2000, after.getRange(rn))
    assertNotNull(transitionOf(after, 1200))
    assertLayoutValid(after)
  }

  @Test
  def removingTransitionSqueezedBySecondSuccessorNoLongerRollsBack(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val p = newContent(1000)
    val a = newContent(1000)
    val b = newContent(1000)
    val rn = newContent(1000)
    place(t0, p, 800 ~~ 1100)
    place(t0, a, 1000 ~~ 1101)
    place(t0, b, 1100 ~~ 1500)
    place(t0, rn, 1101 ~~ 2000)
    val transition = transitionOf(t0, 1100)
    assertNotNull(transition)

    timeline.remove(transition)

    // 不变量校验移除后不再回滚：交界点被 b 的右邻压到 p 的终点之下时，
    // a 会缩到与 p 同终点，区间键 [1000,1100) 与 p-a 转场相同，a 的条目在重建时被覆盖。
    // 这是已知的现状；若恢复回滚语义，本测试需要随之更新。
    val after = timeline.getTrackOrCreate(0)
    assertEquals(1000 ~~ 1100, after.getRange(a))
    assertEquals(800 ~~ 1100, after.getRange(p))
    assertFalse(after.asScala.exists(_ eq a))
  }

  @Test
  def removingTransitionsInBatchSplitsBothOverlaps(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    val c = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    place(t0, c, 1000 ~~ 2000)
    val ab = transitionOf(t0, 700)
    val bc = transitionOf(t0, 1200)
    assertNotNull(ab)
    assertNotNull(bc)

    timeline.remove(List.of(ab, bc))

    val after = timeline.getTrackOrCreate(0)
    assertEquals(0 ~~ 750, after.getRange(a))
    assertEquals(750 ~~ 1250, after.getRange(b))
    assertEquals(1250 ~~ 2000, after.getRange(c))
    assertLayoutValid(after)
  }

  @Test
  def removingAlreadyRemovedTransitionIsNoOp(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    val transition = transitionOf(t0, 700)
    assertNotNull(transition)

    timeline.remove(transition)

    val current = timeline.getTrackOrCreate(0)
    assertSame(current, current.remove(transition))
  }
}
