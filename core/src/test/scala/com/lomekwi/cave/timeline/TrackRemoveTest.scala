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
 * 移除后 [[TrackLayout]] 校验的布局不变量由本测试守护。
 */
class TrackRemoveTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: Timeline = null

  @BeforeEach
  def setUp(): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  private def newContent(duration: Long): Content = new TestCont(duration)

  private def place(track: Track, segment: Content, range: Interval, origin: Long = 0L): Unit = {
    timeline.addOrThrow(track, segment, range, origin)
  }

  private def transitionOf(track: Track, time: Long): Transition = track.get(time) match {
    case t: Transition => t
    case _ => null
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
    TrackLayout.assertValid(after)
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
    TrackLayout.assertValid(after)
  }

  @Test
  def soloEndOfUnaffectedByRemovedLastContent(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    timeline.remove(b)

    // b 离开后 a 右侧的转场索引项仍在，但它已摘表，不得再被当作转场
    val after = timeline.getTrackOrCreate(0)
    assertNull(after.transitionAfter(a))
    assertEquals(1000L, after.soloEndOf(a))
  }

  @Test
  def removedLastContentLeavesTransitionReusableOnReturn(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newContent(1000)
    val b = newContent(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    val transition = transitionOf(t0, 700)
    assertNotNull(transition)

    timeline.remove(b)
    assertNull(timeline.getTrackOrCreate(0).transitionAfter(a))

    val c = newContent(1000)
    place(timeline.getTrackOrCreate(0), c, 500 ~~ 1500)

    // 邻居回来后复用同一个转场对象，身份因此保持稳定
    val after = timeline.getTrackOrCreate(0)
    assertSame(transition, after.transitionAfter(a))
    TrackLayout.assertValid(after)
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
    TrackLayout.assertValid(after)
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
    TrackLayout.assertValid(after)
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
    TrackLayout.assertValid(after)
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
    val remaining = TrackLayout.contentsOf(after)
    assertEquals(1, remaining.size)
    assertSame(b, remaining.head)
    assertEquals(500 ~~ 1500, after.getRange(b))
    assertTrue(after.get(2500).isInstanceOf[Gap])
    TrackLayout.assertValid(after)
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
    TrackLayout.assertValid(after)
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
    TrackLayout.assertValid(after)
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
    TrackLayout.assertValid(after)
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
    TrackLayout.assertValid(after)
  }

  @Test
  def removingTransitionRollsBackWhenSqueezedBySecondSuccessor(): Unit = {
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

    // 交界点被 b 的右邻压回下界之下，两侧内容无法再交叉，消除重叠无解，删除放弃，布局原样保留
    val after = timeline.getTrackOrCreate(0)
    assertEquals(1000 ~~ 1101, after.getRange(a))
    assertEquals(800 ~~ 1100, after.getRange(p))
    assertEquals(1100 ~~ 1500, after.getRange(b))
    assertEquals(1101 ~~ 2000, after.getRange(rn))
    assertSame(transition, transitionOf(after, 1100))
    TrackLayout.assertValid(after)
  }

  @Test
  def removingTransitionKeepsEveryContentVisible(): Unit = {
    // a 的交界点被第二后继压回下界附近时，内容与转场的区间键会争同一个位置。
    // 扫过压回与否的位置，任何内容都不得出现 contains 为真却从迭代里消失的状态，
    // 否则它就成了 placements 里留着、byTime 里缺席的幽灵。
    for (rnLo <- 1101L to 1104L) {
      val tl = new TestProject().timeline
      val p = newContent(1000)
      val a = newContent(1000)
      val b = newContent(1000)
      val rn = newContent(1000)
      tl.addOrThrow(tl.getTrackOrCreate(0), p, 800 ~~ 1100, 0L)
      tl.addOrThrow(tl.getTrackOrCreate(0), a, 1000 ~~ 1101, 0L)
      tl.addOrThrow(tl.getTrackOrCreate(0), b, 1100 ~~ 1500, 0L)
      tl.addOrThrow(tl.getTrackOrCreate(0), rn, rnLo ~~ (rnLo + 1000), 0L)
      val transition = tl.getTrackOrCreate(0).get(1100) match {
        case t: Transition => t
        case _ => null
      }
      assertNotNull(transition)

      tl.remove(transition)

      val after = tl.getTrackOrCreate(0)
      for (s <- Vector(p, a, b, rn)) {
        if (after.contains(s) != after.asScala.exists(_ eq s)) {
          fail("内容在轨道反查里存在却从迭代中消失: " + s + " rnLo=" + rnLo)
        }
      }
      TrackLayout.assertValid(after)
    }
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
    TrackLayout.assertValid(after)
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
