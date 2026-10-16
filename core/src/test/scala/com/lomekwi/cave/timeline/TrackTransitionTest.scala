package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.TestProject

import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull, assertNull, assertSame, assertThrows, assertTrue}
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 转场拓扑的模型级测试。内容保留覆盖完整的区间，转场是相邻两块内容的重叠区，
 * 三者共存于同一张按区间索引的表里。
 */
class TrackTransitionTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: TestTimeline = null

  @BeforeEach
  def setUp(): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  private def newSegment(duration: Long): Content = new TestCont(duration)

  private def place(track: Track, segment: Content, interval: Interval, origin: Long = 0L): Unit = {
    timeline.addOrThrow(track, segment, interval, origin)
  }

  private def transitionOf(track: Track, time: Long): Transition = track.get(time) match {
    case t: Transition => t
    case _ => null
  }

  /** 不能构造转场的内容，createTransition 落到它身上即失败，用来验证兜底走向。 */
  private class NoTransitionCont(duration: Long) extends TestCont(duration) {
    override def canCreateTransitionWith(other: Content): Boolean = false

    override def createTransition(other: Content): Transition =
      throw new UnsupportedOperationException("转场不应由本片段构造")
  }

  @Test
  def overlappingContentsKeepFullIntervalsAndShareTransition(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    // 两侧内容都保留自己的完整区间，转场单独占据交集
    assertEquals(0 ~~ 1000, t0.getInterval(a))
    assertEquals(500 ~~ 1500, t0.getInterval(b))
    assertEquals(500 ~~ 1000, t0.getInterval(transitionOf(t0, 600)))
  }

  @Test
  def getWalksThroughContentTransitionContent(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    val transition = transitionOf(t0, 600)
    assertNotNull(transition)

    assertSame(a, t0.get(499))
    assertSame(transition, t0.get(500))
    assertSame(transition, t0.get(999))
    assertSame(b, t0.get(1000))
    assertSame(b, t0.get(1499))
    assertTrue(t0.get(1500).isInstanceOf[Gap])
  }

  @Test
  def intervalAtPrefersTransitionOverCoveringContents(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    assertEquals(0 ~~ 1000, t0.intervalAt(100))
    assertEquals(500 ~~ 1000, t0.intervalAt(600))
    assertEquals(500 ~~ 1500, t0.intervalAt(1200))
  }

  @Test
  def threeWayOverlapIsRejected(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    val c = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    // c 放在 [800,1800) 会与 a 和 b 同时重叠，隔项不重叠被破坏
    assertThrows(classOf[IllegalArgumentException], () => place(t0, c, 800 ~~ 1800))
    assertEquals(3, t0.size)
  }

  @Test
  def overlapCannotSwallowNeighbour(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(100000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    val applied: Long = timeline.setEnd(Seq(a), 100000)

    assertEquals(499, applied)
    assertEquals(0 ~~ 1499, t0.getInterval(a))
    assertEquals(500 ~~ 1500, t0.getInterval(b))
  }

  @Test
  def endExtensionStopsBeforeSecondSuccessor(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(100000)
    val b = newSegment(100000)
    val c = newSegment(100000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 2000)
    place(t0, c, 1500 ~~ 2500)

    val applied: Long = timeline.setEnd(Seq(a), 100000)

    assertEquals(500, applied)
    assertEquals(0 ~~ 1500, t0.getInterval(a))
  }

  @Test
  def untouchedTransitionKeepsIdentity(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    val c = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    place(t0, c, 2000 ~~ 3000)
    val transition = transitionOf(t0, 600)
    assertNotNull(transition)

    // 平移远处的内容，a 与 b 之间的转场不受影响，应当复用同一个对象
    timeline.moveTime(Seq(c), 100)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(2100 ~~ 3100, after.getInterval(c))
    assertSame(transition, transitionOf(after, 600))
  }

  @Test
  def removingTransitionSplitsOverlapInHalf(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)
    val transition = transitionOf(t0, 600)
    assertNotNull(transition)

    timeline.remove(transition)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(0 ~~ 750, after.getInterval(a))
    assertEquals(750 ~~ 1500, after.getInterval(b))
    assertNull(transitionOf(after, 700))
  }

  @Test
  def removingContentLeavesNeighboursUntouched(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = newSegment(1000)
    val c = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 1500 ~~ 2500)
    place(t0, c, 2000 ~~ 3000) // 与 b 重叠

    timeline.remove(b)

    val after = timeline.getTrackOrCreate(0)
    assertEquals(0 ~~ 1000, after.getInterval(a))
    assertEquals(2000 ~~ 3000, after.getInterval(c))
    assertNull(transitionOf(after, 600))
  }

  @Test
  def shrinkingContentCannotFallInsideLeftNeighbour(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(100000)
    val c = newSegment(100000)
    val b = newSegment(100000)
    place(t0, a, 0 ~~ 1000)
    place(t0, c, 500 ~~ 2000)
    place(t0, b, 1200 ~~ 2500)

    // c 缩短到落进 a 内部时会变成 a 的真子集，终点必须留在 a 的终点之后
    val applied: Long = timeline.setEnd(Seq(c), -100000)

    assertEquals(-999, applied)
    assertEquals(500 ~~ 1001, t0.getInterval(c))
    assertEquals(0 ~~ 1000, t0.getInterval(a))
  }

  @Test
  def leftCannotCreateFallsBackToRight(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = new NoTransitionCont(1000)
    val b = newSegment(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    // 前段不能构造，兜底由后段构造，重叠照常成为转场
    assertNotNull(transitionOf(t0, 600))
  }

  @Test
  def rightCannotCreateStillUsesLeft(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000)
    val b = new NoTransitionCont(1000)
    place(t0, a, 0 ~~ 1000)
    place(t0, b, 500 ~~ 1500)

    // 前段能构造时仍走前段，后段不能构造不构成障碍
    assertNotNull(transitionOf(t0, 600))
  }

  @Test
  def neitherSideCanCreateOverlapIsRejected(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = new NoTransitionCont(1000)
    val b = new NoTransitionCont(1000)
    place(t0, a, 0 ~~ 1000)

    assertThrows(classOf[IllegalArgumentException], () => place(t0, b, 500 ~~ 1500))
    assertEquals(1, t0.size)
  }
}
