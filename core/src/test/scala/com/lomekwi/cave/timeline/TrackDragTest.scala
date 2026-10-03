package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.TestProject

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotNull, assertNotSame, assertSame, assertTrue}
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.util.{List, Set}

import com.lomekwi.cave.pipeline.{Content, Gap, Segment, Transition}

import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * 拖拽 API 的模型级测试，验证截断语义：一次调用就把 deltaTime 同向截到最大可用偏移量并应用，
 * 返回值是实际应用的偏移量，0 表示未移动。
 */
class TrackDragTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: Timeline = null

  @BeforeEach
  def setUp(): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  private def newSegment(duration: Long): Segment = {
    new TestCont(duration)
  }

  private def place(track: Track, segment: Segment, range: Interval): Unit = {
    timeline.addOrThrow(track, segment, range, 0L)
  }

  private def placeAt(track: Track, segment: Segment, range: Interval, origin: Long): Unit = {
    timeline.addOrThrow(track, segment, range, origin)
  }

  /** 该时间点上的片段；落在空隙或轨道外时为 null。 */
  private def srcAt(track: Track, time: Long): Segment = track.get(time) match {
    case s: Segment => s
    case _: Gap => null
  }

  @Test
  def groupMayContainTransition(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    place(t0, a, 0L ~~ 1000_000L)
    place(t0, b, 800_000L ~~ 1800_000L)

    val tr = t0.transitionBetween(a.asInstanceOf[Content], b.asInstanceOf[Content])
    assertNotNull(tr)

    val group = timeline.newGroup()
    assertTrue(group.add(a))
    assertTrue(group.add(tr))
    assertTrue(group.add(b))
    assertEquals(3, group.size())
    assertSame(group, timeline.getGroup(tr))
  }

  @Test
  def movingGroupWithTransitionDoesNotShiftItTwice(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    place(t0, a, 0L ~~ 1000_000L)
    place(t0, b, 800_000L ~~ 1800_000L)

    val tr = t0.transitionBetween(a.asInstanceOf[Content], b.asInstanceOf[Content])
    assertNotNull(tr)
    assertEquals(800_000L ~~ 1000_000L, t0.getRange(tr))

    // 两侧内容都在搬运范围内，转场随它们平移即可，不能再被单独平移一次
    val members = new java.util.ArrayList[Segment]()
    members.add(a)
    members.add(tr)
    members.add(b)
    assertEquals(100_000L, timeline.moveTime(members, 100_000L))

    assertEquals(100_000L ~~ 1100_000L, t0.getRange(a))
    assertEquals(900_000L ~~ 1900_000L, t0.getRange(b))
    assertEquals(900_000L ~~ 1100_000L, t0.getRange(tr))
  }

  @Test
  def groupMoveForwardStopsAtSecondSuccessor(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val x: Segment = newSegment(1000_000L)
    val y: Segment = newSegment(1000_000L)
    val n: Segment = newSegment(1000_000L)
    place(t0, x, TrackDragTest.rng(0, 1000))
    place(t0, y, TrackDragTest.rng(500, 1500))
    place(t0, n, TrackDragTest.rng(1000, 2000))

    // x 前移后会与 n 隔项重叠，整组因此一步都动不了，而不是探出一个放不下去的偏移量
    assertEquals(0L, timeline.moveTime(List.of(x, y), 100))
    assertEquals(TrackDragTest.rng(0, 1000), t0.getRange(x))
    assertEquals(TrackDragTest.rng(500, 1500), t0.getRange(y))
    assertEquals(TrackDragTest.rng(1000, 2000), t0.getRange(n))
  }

  @Test
  def groupMoveBackwardStopsAtSecondPredecessor(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val p: Segment = newSegment(1000_000L)
    val x: Segment = newSegment(1000_000L)
    val y: Segment = newSegment(1000_000L)
    val n: Segment = newSegment(1000_000L)
    place(t0, p, TrackDragTest.rng(0, 1000))
    place(t0, x, TrackDragTest.rng(1000, 2000))
    place(t0, y, TrackDragTest.rng(1500, 2500))
    place(t0, n, TrackDragTest.rng(2000, 3000))

    // 再左移 y 就会与 p 隔项重叠，上限是 y 的起点贴到 p 的终点
    assertEquals(-500L, timeline.moveTime(List.of(x, y), -600))
    assertEquals(TrackDragTest.rng(500, 1500), t0.getRange(x))
    assertEquals(TrackDragTest.rng(1000, 2000), t0.getRange(y))
    assertEquals(TrackDragTest.rng(0, 1000), t0.getRange(p))
  }

  @Test
  def groupMoveForwardUsesNeighbourNotFarContent(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val d: Segment = newSegment(1000_000L)
    val a: Segment = newSegment(1000_000L)
    val b: Segment = newSegment(1000_000L)
    val c: Segment = newSegment(1000_000L)
    val e: Segment = newSegment(1000_000L)
    place(t0, d, TrackDragTest.rng(0, 3000))
    place(t0, a, TrackDragTest.rng(2000, 5000))
    place(t0, b, TrackDragTest.rng(4000, 7000))
    place(t0, c, TrackDragTest.rng(6000, 9000))
    place(t0, e, TrackDragTest.rng(8000, 11000))

    // 中间成员 b 的右邻居是 c，与 e 隔项，上限由 b 与 e 的隔项关系定，不是按 b 与 e 相邻算出来的 1999
    assertEquals(1000L, timeline.moveTime(List.of(a, b, c), 2000))
    assertEquals(TrackDragTest.rng(3000, 6000), t0.getRange(a))
    assertEquals(TrackDragTest.rng(5000, 8000), t0.getRange(b))
    assertEquals(TrackDragTest.rng(7000, 10000), t0.getRange(c))
    assertEquals(TrackDragTest.rng(8000, 11000), t0.getRange(e))
  }

  @Test
  def movingGroupKeepsInternalTransitionIdentity(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    // 组的两侧都还有别的内容，成员的右邻居在放回中途会暂时缺席
    val d = newSegment(1000_000L)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    val c = newSegment(1000_000L)
    val e = newSegment(1000_000L)
    place(t0, d, TrackDragTest.rng(0, 3000))
    place(t0, a, TrackDragTest.rng(2000, 5000))
    place(t0, b, TrackDragTest.rng(4000, 7000))
    place(t0, c, TrackDragTest.rng(6000, 9000))
    place(t0, e, TrackDragTest.rng(8000, 11000))
    val ab = t0.transitionBetween(a.asInstanceOf[Content], b.asInstanceOf[Content])
    val bc = t0.transitionBetween(b.asInstanceOf[Content], c.asInstanceOf[Content])
    assertNotNull(ab)
    assertNotNull(bc)

    assertEquals(500L, timeline.moveTime(List.of(a, b, c), 500L))

    val after = timeline.getTrackOrCreate(0)
    assertEquals(TrackDragTest.rng(2500, 5500), after.getRange(a))
    assertEquals(TrackDragTest.rng(4500, 7500), after.getRange(b))
    assertEquals(TrackDragTest.rng(6500, 9500), after.getRange(c))
    // 组内相邻对的转场随两侧一起平移，应当仍是原来那两个对象
    assertSame(ab, after.transitionBetween(a.asInstanceOf[Content], b.asInstanceOf[Content]))
    assertSame(bc, after.transitionBetween(b.asInstanceOf[Content], c.asInstanceOf[Content]))
  }

  @Test
  def freshTrackIsEmpty(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)

    assertTrue(t0.isEmpty)
    assertEquals(0, t0.length)
    assertTrue(srcAt(t0, 0) == null)
    assertTrue(srcAt(t0, TrackDragTest.rng(0, 100).hi) == null)
  }

  @Test
  def segmentRangeIsQueryableAndGapIsImplicit(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))

    assertTrue(t0.get(100).isInstanceOf[Gap])
    assertEquals(TrackDragTest.rng(100, Long.MaxValue), t0.rangeAt(100))
  }

  @Test
  def removeCoalescesSurroundingGap(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Segment = newSegment(100)
    val b: Segment = newSegment(100)
    place(t0, a, TrackDragTest.rng(0, 100))
    place(t0, b, TrackDragTest.rng(200, 300))

    timeline.remove(a)
    assertTrue(srcAt(t0, 50) == null)
    assertEquals(TrackDragTest.rng(0, 200), t0.rangeAt(50))
    assertEquals(TrackDragTest.rng(200, 300), t0.rangeAt(250))
  }

  @Test
  def addPlacesSegmentAndSetsRangeAndTrack(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    assertSame(s, srcAt(t0, 50))
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))
    assertSame(t0, timeline.findTrackOf(s))
  }

  @Test
  def tryAddAtOccupiedRangeDoesNotReplace(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Segment = newSegment(100)
    val b: Segment = newSegment(100)
    place(t0, a, TrackDragTest.rng(0, 100))

    val shift: Long = timeline.tryAdd(t0, b, TrackDragTest.rng(0, 100), 0L)

    assertTrue(shift != 0)
    assertSame(a, srcAt(t0, 50))
    assertEquals(1, countOn(t0))
  }

  @Test
  def removeSegmentRemovesIt(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    timeline.remove(s)
    assertTrue(srcAt(t0, 50) == null)
    assertTrue(t0.isEmpty)
  }

  @Test
  def removeCollectionRemovesAll(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Segment = newSegment(100)
    val b: Segment = newSegment(100)
    place(t0, a, TrackDragTest.rng(0, 100))
    place(t0, b, TrackDragTest.rng(500, 600))

    timeline.remove(List.of(a, b))
    assertTrue(t0.isEmpty)
  }

  @Test
  def removeDetachesSegmentFromItsGroup(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Segment = newSegment(100)
    val b: Segment = newSegment(100)
    place(t0, a, TrackDragTest.rng(0, 100))
    place(t0, b, TrackDragTest.rng(500, 600))
    val group: SegmentGroup = timeline.newGroup()
    group.add(a)
    group.add(b)

    Using.resource(timeline.record()) { _ =>
      timeline.remove(a)
    }

    // 删除必须同步移出组，否则组里残留的片段会被整组选中带出，在找不到轨道时崩溃
    assertTrue(timeline.findTrackOf(a) == null)
    assertTrue(!group.contains(a))
    assertTrue(group.contains(b))
    assertSame(group, timeline.getGroup(b))

    project.undoManager.undo()
    assertSame(group, timeline.getGroup(a))
    assertSame(group, timeline.getGroup(b))

    project.undoManager.redo()
    assertTrue(!group.contains(a))
  }

  @Test
  def removeInvalidatesTrackLength(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Segment = newSegment(100)
    val b: Segment = newSegment(100)
    place(t0, a, TrackDragTest.rng(0, 100))
    place(t0, b, TrackDragTest.rng(500, 600))
    assertEquals(600, t0.length)

    timeline.remove(b)
    assertEquals(100, t0.length)
  }

  @Test
  def timelineLengthFollowsEdits(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    place(t0, s, TrackDragTest.rng(0, 100))
    assertEquals(100, timeline.getLength)

    timeline.moveTime(List.of(s), 900)
    assertEquals(1000, timeline.getLength)

    timeline.remove(s)
    assertEquals(0, timeline.getLength)
  }

  @Test
  def moveSingleSegmentByTimePreservesDurationAndOffsetsOrigin(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    placeAt(t0, s, TrackDragTest.rng(0, 100), 1000)

    val applied: Long = timeline.moveTime(List.of(s), 200)

    assertEquals(200, applied)
    assertEquals(TrackDragTest.rng(200, 300), t0.getRange(s))
    assertSame(t0, timeline.findTrackOf(s))
    assertEquals(1200, t0.getOrigin(s))
  }

  @Test
  def moveSingleSegmentAcrossTracks(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val s: Segment = newSegment(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    val applied: Int = timeline.moveTrack(List.of(s), 1)

    assertEquals(1, applied)
    assertSame(t1, timeline.findTrackOf(s))
    assertEquals(TrackDragTest.rng(0, 100), t1.getRange(s))
    assertTrue(t0.isEmpty)
    assertSame(s, srcAt(t1, 50))
  }

  @Test
  def moveMultiSelectMovesAllMembersTogether(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val a: Segment = newSegment(100)
    val b: Segment = newSegment(100)
    place(t0, a, TrackDragTest.rng(0, 100))
    place(t1, b, TrackDragTest.rng(500, 600))

    val applied: Long = timeline.moveTime(List.of(a, b), 1000)

    assertEquals(1000, applied)
    assertEquals(TrackDragTest.rng(1000, 1100), t0.getRange(a))
    assertEquals(TrackDragTest.rng(1500, 1600), t1.getRange(b))
    assertSame(t0, timeline.findTrackOf(a))
    assertSame(t1, timeline.findTrackOf(b))
  }

  @Test
  def moveTimeBackwardClampsAtZeroForLeadingMember(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Segment = newSegment(100)
    val b: Segment = newSegment(100)
    place(t0, a, TrackDragTest.rng(50, 150))
    place(t0, b, TrackDragTest.rng(200, 300))

    // 锚定 b 拖到 0（整体偏移 -200），组内更靠前的 a 起点 50 只能到 0，
    // 整组偏移被夹到 -50，a/b 都不能越过时间轴 0
    val applied: Long = timeline.moveTime(List.of(a, b), -200)

    assertEquals(-50, applied)
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(a))
    assertEquals(TrackDragTest.rng(150, 250), t0.getRange(b))
  }

  @Test
  def moveIntoOccupiedSpotCreatesTransition(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val mover: Segment = newSegment(100)
    val obstacle: Segment = newSegment(1000)
    place(t0, mover, TrackDragTest.rng(0, 100))
    // 障碍占据 [100, 1100)，把 mover 挪到 [150,250) 会与它重叠
    place(t0, obstacle, TrackDragTest.rng(100, 1100))

    // 可建转场，上限是 mover 起点退到障碍起点前一微秒，请求 150 截到 99
    val applied: Long = timeline.moveTime(List.of(mover), 150)

    assertEquals(99, applied)
    // 内容保留完整区间，转场是两者的重叠区
    assertEquals(TrackDragTest.rng(99, 199), t0.getRange(mover))
    val transition = srcAt(t0, 150)
    assertTrue(transition.isInstanceOf[Transition])
    assertEquals(TrackDragTest.rng(100, 199), t0.getRange(transition))
    assertEquals(TrackDragTest.rng(100, 1100), t0.getRange(obstacle))
  }

  @Test
  def moveTimeOverlapsObstacleBuildsTransition(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val mover: Segment = newSegment(100)
    val obstacle: Segment = newSegment(100)
    place(t0, mover, TrackDragTest.rng(0, 100))
    place(t0, obstacle, TrackDragTest.rng(300, 400))

    // 请求 +250：终点进到障碍里，重叠区间 [300,350) 成为转场
    val applied: Long = timeline.moveTime(List.of(mover), 250)
    assertEquals(250, applied)
    assertEquals(TrackDragTest.rng(250, 350), t0.getRange(mover))
    val transition = srcAt(t0, 320)
    assertTrue(transition.isInstanceOf[Transition])
    assertEquals(TrackDragTest.rng(300, 350), t0.getRange(transition))
    assertEquals(TrackDragTest.rng(300, 400), t0.getRange(obstacle))

    // 移回 50，转场消失，被吃掉的尾部还回来
    assertEquals(-50, timeline.moveTime(List.of(mover), -50))
    assertEquals(TrackDragTest.rng(200, 300), t0.getRange(mover))
    assertEquals(TrackDragTest.rng(300, 400), t0.getRange(obstacle))
    assertFalse(t0.asScala.exists(_.isInstanceOf[Transition]))
  }

  @Test
  def moveAcrossTrackIntoOccupiedSpotDoesNotApply(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val mover: Segment = newSegment(100)
    val obstacle: Segment = newSegment(1000)
    place(t0, mover, TrackDragTest.rng(0, 100))
    place(t1, obstacle, TrackDragTest.rng(0, 1000))

    // 目标轨道同区间被占据且方向上无更近的可落点，偏移截断为 0
    val applied: Int = timeline.moveTrack(List.of(mover), 1)

    assertEquals(0, applied)
    assertSame(t0, timeline.findTrackOf(mover))
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(mover))
    assertSame(obstacle, srcAt(t1, 50))
  }

  @Test
  def moveTrackKeepsUnboundRangeOfSegmentAdjacentToTransition(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val a: Segment = newSegment(100)
    val b: Segment = newSegment(100)
    place(t0, a, TrackDragTest.rng(0, 100))
    // 与 a 重叠建转场：a=[0,100) t=[80,100) b=[80,180)，b 的 origin 是 80
    placeAt(t0, b, TrackDragTest.rng(80, 180), 80)
    assertEquals(TrackDragTest.rng(80, 180), t0.getRange(b))
    assertEquals(TrackDragTest.rng(80, 100), t0.getRange(srcAt(t0, 90)))

    val applied: Int = timeline.moveTrack(List.of(b), 1)

    assertEquals(1, applied)
    // 搬运的是 b 的完整区间，origin 不变
    assertEquals(TrackDragTest.rng(80, 180), t1.getRange(b))
    assertEquals(80, t1.getOrigin(b))
    // 轨道 0 的 a 补占转场段，转场消失
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(a))
    assertFalse(t0.asScala.exists(_.isInstanceOf[Transition]))
  }

  @Test
  def setStartSlidesFrontKeepsEndFixed(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    val applied: Long = timeline.setStart(List.of(s), 30)

    assertEquals(30, applied)
    assertEquals(TrackDragTest.rng(30, 100), t0.getRange(s))
    assertSame(t0, timeline.findTrackOf(s))
    assertEquals(0, t0.getOrigin(s))
  }

  @Test
  def setEndSlidesBackKeepsStartFixed(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    place(t0, s, TrackDragTest.rng(0, 50))

    val applied: Long = timeline.setEnd(List.of(s), 50)

    assertEquals(50, applied)
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))
  }

  @Test
  def setEndShrinksBackwards(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    val applied: Long = timeline.setEnd(List.of(s), -20)

    assertEquals(-20, applied)
    assertEquals(TrackDragTest.rng(0, 80), t0.getRange(s))
  }

  @Test
  def setStartIntoObstacleCreatesTransition(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    val obstacle: Segment = newSegment(100)
    place(t0, obstacle, TrackDragTest.rng(0, 100)) // 左侧障碍占住 [0,100)
    place(t0, s, TrackDragTest.rng(100, 200))      // 把 s 起点往左推到 50 会与障碍重叠

    // 可建转场，起点最多左移到障碍终点前一微秒
    val applied: Long = timeline.setStart(List.of(s), -50)

    assertEquals(-50, applied)
    assertEquals(TrackDragTest.rng(50, 200), t0.getRange(s))
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(obstacle))
    val transition = srcAt(t0, 75)
    assertTrue(transition.isInstanceOf[Transition])
    assertEquals(TrackDragTest.rng(50, 100), t0.getRange(transition))
  }

  @Test
  def setStartBackwardClampsToNearestObstacleEdge(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(200)
    val obstacle: Segment = newSegment(50)
    place(t0, s, TrackDragTest.rng(50, 100))      // origin 为 0，最小起点也是 0
    place(t0, obstacle, TrackDragTest.rng(0, 30)) // 占住 [0,30)

    // 想拖到 -10，但重叠最多让障碍只剩 1µs，截到起点 1，重叠区成为转场
    val applied: Long = timeline.setStart(List.of(s), -60)

    assertEquals(-49, applied)
    assertEquals(TrackDragTest.rng(1, 100), t0.getRange(s))
    assertEquals(TrackDragTest.rng(0, 30), t0.getRange(obstacle))
    val transition = srcAt(t0, 10)
    assertTrue(transition.isInstanceOf[Transition])
    assertEquals(TrackDragTest.rng(1, 30), t0.getRange(transition))
  }

  @Test
  def setEndForwardClampsToNearestObstacleEdge(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    val obstacle: Segment = newSegment(40)
    place(t0, s, TrackDragTest.rng(0, 50))
    place(t0, obstacle, TrackDragTest.rng(80, 120)) // 占住 [80,120)

    // 想拖到 250，素材终点 100 比重叠上限 119 更近，截到 100
    val applied: Long = timeline.setEnd(List.of(s), 200)

    assertEquals(50, applied)
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))
    assertEquals(TrackDragTest.rng(80, 120), t0.getRange(obstacle))
    val transition = srcAt(t0, 90)
    assertTrue(transition.isInstanceOf[Transition])
    assertEquals(TrackDragTest.rng(80, 100), t0.getRange(transition))
  }

  @Test
  def groupSetEndAdjacentMembersOverlap(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Segment = newSegment(100)
    val b: Segment = newSegment(100)
    placeAt(t0, a, TrackDragTest.rng(0, 100), 500)   // 允许尾端伸展
    placeAt(t0, b, TrackDragTest.rng(100, 200), 600) // 与 a 相邻

    // 两者同时伸尾 50：a 的尾巴盖到 b 上形成转场，b 自身也变长
    val applied: Long = timeline.setEnd(List.of(a, b), 50)
    assertEquals(50, applied)
    assertEquals(TrackDragTest.rng(0, 150), t0.getRange(a))
    assertEquals(TrackDragTest.rng(100, 250), t0.getRange(b))
    val transition = srcAt(t0, 125)
    assertTrue(transition.isInstanceOf[Transition])
    assertEquals(TrackDragTest.rng(100, 150), t0.getRange(transition))
  }

  @Test
  def groupSetStartAdjacentMembersMoveTogether(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Segment = newSegment(100)
    val b: Segment = newSegment(100)
    place(t0, a, TrackDragTest.rng(100, 200))
    place(t0, b, TrackDragTest.rng(200, 300)) // 与 a 相邻

    // a 起点前移到 50，b 起点前移到 150 与 a 重叠，重叠区间成为转场
    val applied: Long = timeline.setStart(List.of(a, b), -50)
    assertEquals(-50, applied)
    assertEquals(TrackDragTest.rng(50, 200), t0.getRange(a))
    val transition = srcAt(t0, 175)
    assertTrue(transition.isInstanceOf[Transition])
    assertEquals(TrackDragTest.rng(150, 200), t0.getRange(transition))
    assertEquals(TrackDragTest.rng(150, 300), t0.getRange(b))

    // 再前移 50：a 贴住 0 点，b 也再前移 50，重叠区随之左移
    assertEquals(-50, timeline.setStart(List.of(a, b), -50))
    assertEquals(TrackDragTest.rng(0, 200), t0.getRange(a))
    val kept = srcAt(t0, 175)
    assertTrue(kept.isInstanceOf[Transition])
    assertEquals(TrackDragTest.rng(100, 200), t0.getRange(kept))
    assertEquals(TrackDragTest.rng(100, 300), t0.getRange(b))

    // a 已到边界，整组偏移截断为 0
    assertEquals(0, timeline.setStart(List.of(a, b), -50))
    assertEquals(TrackDragTest.rng(0, 200), t0.getRange(a))
    assertEquals(TrackDragTest.rng(100, 300), t0.getRange(b))
  }

  @Test
  def snapTimeSnapsToNearestEdgeWithinThreshold(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Segment = newSegment(100)
    val b: Segment = newSegment(100)
    place(t0, a, TrackDragTest.rng(100, 200))
    place(t0, b, TrackDragTest.rng(400, 500))

    // 距 a 起点 100 仅 5，吸附到 100
    assertEquals(100, timeline.snapTime(105, 10, Set.of[Segment]()))
    // 距 b 终点 500 仅 3，吸附到 500
    assertEquals(500, timeline.snapTime(497, 10, Set.of[Segment]()))
    // 阈值内无更近端点，返回原值
    assertEquals(300, timeline.snapTime(300, 10, Set.of[Segment]()))
    // 阈值外，不吸附
    assertEquals(120, timeline.snapTime(120, 10, Set.of[Segment]()))
  }

  @Test
  def snapTimeIgnoresGivenSegmentsAndSnapsToZero(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Segment = newSegment(100)
    place(t0, a, TrackDragTest.rng(100, 200))

    // ignore 中的片段不参与吸附
    assertEquals(150, timeline.snapTime(150, 10, Set.of(a)))
    // 距 0 比距任何端点都近，吸附到 0
    assertEquals(0, timeline.snapTime(5, 10, Set.of[Segment]()))
  }

  @Test
  def splitSplitsSegmentIntoTwoHalvesWithCorrectRanges(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    placeAt(t0, s, TrackDragTest.rng(0, 100), 1000)

    timeline.split(t0, 40)

    assertEquals(TrackDragTest.rng(0, 40), t0.getRange(s))
    assertSame(s, srcAt(t0, 20))
    assertEquals(1000, t0.getOrigin(s))
    val right: Segment = srcAt(t0, 60)
    assertNotSame(s, right)
    assertEquals(TrackDragTest.rng(40, 100), t0.getRange(right))
    assertSame(t0, timeline.findTrackOf(right))
    // 右半 origin 与左半一致，内容才接得上
    assertEquals(1000, t0.getOrigin(right))
    assertEquals(2, countOn(t0))
  }

  @Test
  def undoRedoMoveSegmentsCommandRestoresState(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val s: Segment = newSegment(100)
    placeAt(t0, s, TrackDragTest.rng(0, 100), 1000)

    val entries = new java.util.ArrayList[UndoManager.TrackEdit]()
    entries.add(UndoManager.TrackEdit(0, t0, t0.remove(s)))
    entries.add(UndoManager.TrackEdit(1, t1, t1.addOrThrow(s, TrackDragTest.rng(50, 150), 1050)))
    project.undoManager.execute(new UndoManager.MoveSegmentsCommand(timeline, entries))

    assertEquals(TrackDragTest.rng(50, 150), t1.getRange(s))
    assertSame(t1, timeline.findTrackOf(s))
    assertEquals(1050, t1.getOrigin(s))

    project.undoManager.undo()
    assertSame(t0, timeline.findTrackOf(s))
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))
    assertEquals(1000, t0.getOrigin(s))

    project.undoManager.redo()
    assertSame(t1, timeline.findTrackOf(s))
    assertEquals(TrackDragTest.rng(50, 150), t1.getRange(s))
    assertEquals(1050, t1.getOrigin(s))
  }

  @Test
  def undoRedoResizeSegmentsCommandRestoresState(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    val before = t0
    val entries = new java.util.ArrayList[UndoManager.TrackEdit]()
    entries.add(UndoManager.TrackEdit(0, before,
      before.remove(s).addOrThrow(s, TrackDragTest.rng(30, 100), 0L)))
    project.undoManager.execute(new UndoManager.ResizeSegmentsCommand(timeline, entries))
    assertEquals(TrackDragTest.rng(30, 100), t0.getRange(s))

    project.undoManager.undo()
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))

    project.undoManager.redo()
    assertEquals(TrackDragTest.rng(30, 100), t0.getRange(s))
  }

  @Test
  def undoRedoSplitSegmentCommandRestoresState(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Segment = newSegment(100)
    place(t0, s, TrackDragTest.rng(0, 100))
    val before = t0
    val after = before.split(40)
    val right: Segment = srcAt(after, 60)

    project.undoManager.execute(new UndoManager.SplitSegmentCommand(timeline, 0, before, after))

    assertEquals(TrackDragTest.rng(0, 40), t0.getRange(s))
    assertEquals(TrackDragTest.rng(40, 100), t0.getRange(right))
    assertEquals(2, countOn(t0))

    project.undoManager.undo()
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))
    assertEquals(1, countOn(t0))
    assertSame(s, srcAt(t0, 50))
  }

  @Test
  def compoundMoveUndoRestoresMultipleSegments(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val a: Segment = newSegment(100)
    placeAt(t0, a, TrackDragTest.rng(0, 100), 1000)
    val b: Segment = newSegment(100)
    placeAt(t1, b, TrackDragTest.rng(500, 600), 2000)

    // 模拟 UI 拖拽，record 期间直接改动模型，关闭时合并为一条复合命令
    Using.resource(timeline.record()) { h =>
      timeline.moveTime(List.of(a, b), 1000)
    }
    assertEquals(TrackDragTest.rng(1000, 1100), t0.getRange(a))
    assertEquals(2000, t0.getOrigin(a))
    assertEquals(TrackDragTest.rng(1500, 1600), t1.getRange(b))
    assertEquals(3000, t1.getOrigin(b))

    project.undoManager.undo()
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(a))
    assertEquals(1000, t0.getOrigin(a))
    assertEquals(TrackDragTest.rng(500, 600), t1.getRange(b))
    assertEquals(2000, t1.getOrigin(b))
  }

  private def countOn(track: Track): Int = track.asScala.size
}

object TrackDragTest {
  private def rng(lo: Long, hi: Long): Interval = {
    lo ~~ hi
  }
}
