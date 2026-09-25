package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.TestProject

import org.junit.jupiter.api.Assertions.{assertEquals, assertNotSame, assertSame, assertTrue}
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.util.{List, Set}

import com.lomekwi.cave.pipeline.{Gap, Source}

import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * 拖拽 API 的模型级测试。
 * 目标为验证截断式（clamp）的探测/应用语义，执行方法一次调用即把 deltaTime
 * 同向截断到最大可用偏移量并应用，返回实际应用的偏移量（0 = 未移动）。
 *
 * 覆盖如下。
 *  - add/remove 基础放置与覆盖
 *  - move（整体平移），无阻碍时精确落位 + origin 同步；有阻碍时截断到与障碍贴合或原地不动
 *  - setStart/setEnd（头/尾裁切），只动对应端点；越界/受阻时截断到边界
 *  - split，一分为二，两侧区间正确
 *  - Undo/redo，统一经 UndoManager 还原/重放
 */
class TrackDragTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: Timeline = null

  @BeforeEach
  def setUp(): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  private def newSrc(duration: Long): Source[?] = {
    new TestSource(duration)
  }

  private def place(track: Track, src: Source[?], range: Interval): Unit = {
    timeline.addOrThrow(track, src, range, 0L)
  }

  private def placeAt(track: Track, src: Source[?], range: Interval, origin: Long): Unit = {
    timeline.addOrThrow(track, src, range, origin)
  }

  /** 该时间点上的片段源；落在空隙或轨道外时为 null。 */
  private def segAt(track: Track, time: Long): Source[?] = track.get(time) match {
    case s: Source[?] => s
    case _: Gap => null
  }

  @Test
  def freshTrackIsEmptyCoveredBySingleGap(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)

    assertTrue(t0.isEmpty)
    assertEquals(0, t0.getLength)
    assertTrue(segAt(t0, 0) == null)
    assertTrue(segAt(t0, TrackDragTest.rng(0, 100).hi) == null)
  }

  @Test
  def elementRangeIsQueryableForBothKinds(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))

    val tail = t0.get(100)
    assertTrue(tail.isInstanceOf[Gap])
    assertEquals(Interval(100, Long.MaxValue), t0.getRange(tail))
  }

  @Test
  def addPlacesSourceAndSetsRangeAndTrack(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    assertSame(s, segAt(t0, 50))
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))
    assertSame(t0, timeline.findTrackOf(s))
  }

  @Test
  def tryAddAtOccupiedRangeDoesNotReplace(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Source[?] = newSrc(100)
    val b: Source[?] = newSrc(100)
    place(t0, a, TrackDragTest.rng(0, 100))

    val shift: Long = timeline.tryAdd(t0, b, TrackDragTest.rng(0, 100), 0L)

    assertTrue(shift != 0)
    assertSame(a, segAt(t0, 50))
    assertEquals(1, countOn(t0))
  }

  @Test
  def removeSourceRemovesIt(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    timeline.remove(s)
    assertTrue(segAt(t0, 50) == null)
    assertTrue(t0.isEmpty)
  }

  @Test
  def removeCollectionRemovesAll(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Source[?] = newSrc(100)
    val b: Source[?] = newSrc(100)
    place(t0, a, TrackDragTest.rng(0, 100))
    place(t0, b, TrackDragTest.rng(500, 600))

    timeline.remove(List.of(a, b))
    assertTrue(t0.isEmpty)
  }

  @Test
  def removeInvalidatesTrackLength(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Source[?] = newSrc(100)
    val b: Source[?] = newSrc(100)
    place(t0, a, TrackDragTest.rng(0, 100))
    place(t0, b, TrackDragTest.rng(500, 600))
    assertEquals(600, t0.getLength)

    timeline.remove(b)
    assertEquals(100, t0.getLength)
  }

  @Test
  def timelineLengthFollowsEdits(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
    place(t0, s, TrackDragTest.rng(0, 100))
    assertEquals(100, timeline.getLength)

    timeline.moveTime(List.of(s), 900)
    assertEquals(1000, timeline.getLength)

    timeline.remove(s)
    assertEquals(0, timeline.getLength)
  }

  @Test
  def moveSingleSourceByTimePreservesDurationAndOffsetsOrigin(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
    placeAt(t0, s, TrackDragTest.rng(0, 100), 1000)

    val applied: Long = timeline.moveTime(List.of(s), 200)

    assertEquals(200, applied)
    assertEquals(TrackDragTest.rng(200, 300), t0.getRange(s))
    assertSame(t0, timeline.findTrackOf(s))
    assertEquals(1200, t0.getOrigin(s))
  }

  @Test
  def moveSingleSourceAcrossTracks(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val s: Source[?] = newSrc(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    val applied: Int = timeline.moveTrack(List.of(s), 1)

    assertEquals(1, applied)
    assertSame(t1, timeline.findTrackOf(s))
    assertEquals(TrackDragTest.rng(0, 100), t1.getRange(s))
    assertTrue(t0.isEmpty)
    assertSame(s, segAt(t1, 50))
  }

  @Test
  def moveMultiSelectMovesAllMembersTogether(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val a: Source[?] = newSrc(100)
    val b: Source[?] = newSrc(100)
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
    val a: Source[?] = newSrc(100)
    val b: Source[?] = newSrc(100)
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
  def moveIntoOccupiedSpotOnSameTrackDoesNotApply(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val mover: Source[?] = newSrc(100)
    val obstacle: Source[?] = newSrc(1000)
    place(t0, mover, TrackDragTest.rng(0, 100))
    // 障碍占据 [100, 1100)，把 mover 挪到 [150,250) 会撞上它
    place(t0, obstacle, TrackDragTest.rng(100, 1100))

    // 右侧紧贴障碍，最大可用偏移为 0，完全无法移动
    val applied: Long = timeline.moveTime(List.of(mover), 150)

    assertEquals(0, applied)
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(mover))
    assertSame(obstacle, segAt(t0, 200))
  }

  @Test
  def moveTimeClampsAtObstacleEdge(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val mover: Source[?] = newSrc(100)
    val obstacle: Source[?] = newSrc(100)
    place(t0, mover, TrackDragTest.rng(0, 100))
    place(t0, obstacle, TrackDragTest.rng(300, 400))

    // 请求 +250，最多移到与障碍贴合（+200），一次调用直接应用
    val applied: Long = timeline.moveTime(List.of(mover), 250)
    assertEquals(200, applied)
    assertEquals(TrackDragTest.rng(200, 300), t0.getRange(mover))
    assertSame(obstacle, segAt(t0, 350))

    // 请求在可用范围内时全额应用（返回带符号的实际偏移）
    assertEquals(-50, timeline.moveTime(List.of(mover), -50))
    assertEquals(TrackDragTest.rng(150, 250), t0.getRange(mover))
  }

  @Test
  def moveAcrossTrackIntoOccupiedSpotDoesNotApply(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val mover: Source[?] = newSrc(100)
    val obstacle: Source[?] = newSrc(1000)
    place(t0, mover, TrackDragTest.rng(0, 100))
    place(t1, obstacle, TrackDragTest.rng(0, 1000)) // 目标轨道同区间被占据

    // 目标轨道被占据且方向上无更近的可落点，偏移截断为 0，保持原位
    val applied: Int = timeline.moveTrack(List.of(mover), 1)

    assertEquals(0, applied)
    assertSame(t0, timeline.findTrackOf(mover))
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(mover))
    assertSame(obstacle, segAt(t1, 50))
  }

  @Test
  def setStartSlidesFrontKeepsEndFixed(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
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
    val s: Source[?] = newSrc(100)
    place(t0, s, TrackDragTest.rng(0, 50))

    val applied: Long = timeline.setEnd(List.of(s), 50)

    assertEquals(50, applied)
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))
  }

  @Test
  def setEndShrinksBackwards(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    val applied: Long = timeline.setEnd(List.of(s), -20)

    assertEquals(-20, applied)
    assertEquals(TrackDragTest.rng(0, 80), t0.getRange(s))
  }

  @Test
  def setStartIntoOccupiedSpotDoesNotApply(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
    val obstacle: Source[?] = newSrc(100)
    place(t0, obstacle, TrackDragTest.rng(0, 100)) // 左侧障碍占住 [0,100)
    place(t0, s, TrackDragTest.rng(100, 200))      // 把 s 起点往左推到 50 会撞上它

    // 左侧紧贴障碍，最大可用偏移为 0，完全无法移动
    val applied: Long = timeline.setStart(List.of(s), -50)

    assertEquals(0, applied)
    assertEquals(TrackDragTest.rng(100, 200), t0.getRange(s))
    assertSame(obstacle, segAt(t0, 50))
  }

  @Test
  def setStartBackwardClampsToNearestObstacleEdge(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(200)
    val obstacle: Source[?] = newSrc(50)
    place(t0, s, TrackDragTest.rng(50, 100))      // 前端已被裁切，origin=0 → minStart=0
    place(t0, obstacle, TrackDragTest.rng(0, 30)) // 占住 [0,30)

    // 想把前端拖到 -10（delta=-60），minStart 允许回到 0，但 obstacle 终点 30 更近，
    // 一次调用直接左移到与障碍贴合（起点 30，偏移 -20）
    val applied: Long = timeline.setStart(List.of(s), -60)

    assertEquals(-20, applied)
    assertEquals(TrackDragTest.rng(30, 100), t0.getRange(s))
    assertSame(obstacle, segAt(t0, 10))
  }

  @Test
  def setEndForwardClampsToNearestObstacleEdge(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
    val obstacle: Source[?] = newSrc(40)
    place(t0, s, TrackDragTest.rng(0, 50))
    place(t0, obstacle, TrackDragTest.rng(80, 120)) // 占住 [80,120)

    // 想把尾端拖到 250（delta=200），maxEnd=100 与 obstacle 起点 80 相比 80 更近，
    // 一次调用直接右移到与障碍贴合（终点 80，偏移 +30）
    val applied: Long = timeline.setEnd(List.of(s), 200)

    assertEquals(30, applied)
    assertEquals(TrackDragTest.rng(0, 80), t0.getRange(s))
    assertSame(obstacle, segAt(t0, 90))
  }

  @Test
  def groupSetEndAdjacentMembersDoNotOverlap(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Source[?] = newSrc(100)
    val b: Source[?] = newSrc(100)
    placeAt(t0, a, TrackDragTest.rng(0, 100), 500)   // 允许尾端伸展
    placeAt(t0, b, TrackDragTest.rng(100, 200), 600) // 与 a 相邻

    // 伸展被后一个成员的起点挡住，整组偏移截断为 0，模型不变
    val applied: Long = timeline.setEnd(List.of(a, b), 50)
    assertEquals(0, applied)
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(a))
    assertEquals(TrackDragTest.rng(100, 200), t0.getRange(b))
    assertSame(a, segAt(t0, 50))
    assertSame(b, segAt(t0, 150))

    // 再次请求仍截断为 0，成员仍不重叠
    assertEquals(0, timeline.setEnd(List.of(a, b), 50))
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(a))
    assertEquals(TrackDragTest.rng(100, 200), t0.getRange(b))
  }

  @Test
  def groupSetStartAdjacentMembersDoNotOverlap(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Source[?] = newSrc(100)
    val b: Source[?] = newSrc(100)
    place(t0, a, TrackDragTest.rng(100, 200))
    place(t0, b, TrackDragTest.rng(200, 300)) // 与 a 相邻

    // 前移被前一个成员的终点挡住，整组偏移截断为 0，模型不变
    val applied: Long = timeline.setStart(List.of(a, b), -50)
    assertEquals(0, applied)
    assertEquals(TrackDragTest.rng(100, 200), t0.getRange(a))
    assertEquals(TrackDragTest.rng(200, 300), t0.getRange(b))
    assertSame(a, segAt(t0, 100))
    assertSame(b, segAt(t0, 250))

    // 再次请求仍截断为 0，成员仍不重叠
    assertEquals(0, timeline.setStart(List.of(a, b), -50))
    assertEquals(TrackDragTest.rng(100, 200), t0.getRange(a))
    assertEquals(TrackDragTest.rng(200, 300), t0.getRange(b))
  }

  @Test
  def snapTimeSnapsToNearestEdgeWithinThreshold(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Source[?] = newSrc(100)
    val b: Source[?] = newSrc(100)
    place(t0, a, TrackDragTest.rng(100, 200))
    place(t0, b, TrackDragTest.rng(400, 500))

    // 距 a 起点 100 仅 5，吸附到 100
    assertEquals(100, timeline.snapTime(105, 10, Set.of[Source[?]]()))
    // 距 b 终点 500 仅 3，吸附到 500
    assertEquals(500, timeline.snapTime(497, 10, Set.of[Source[?]]()))
    // 阈值内无更近端点，返回原值
    assertEquals(300, timeline.snapTime(300, 10, Set.of[Source[?]]()))
    // 阈值外，不吸附
    assertEquals(120, timeline.snapTime(120, 10, Set.of[Source[?]]()))
  }

  @Test
  def snapTimeIgnoresGivenSourcesAndSnapsToZero(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val a: Source[?] = newSrc(100)
    place(t0, a, TrackDragTest.rng(100, 200))

    // ignore 中的源不参与吸附
    assertEquals(150, timeline.snapTime(150, 10, Set.of(a)))
    // 距 0 比距任何端点都近，吸附到 0
    assertEquals(0, timeline.snapTime(5, 10, Set.of[Source[?]]()))
  }

  @Test
  def splitSplitsSourceIntoTwoHalvesWithCorrectRanges(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
    placeAt(t0, s, TrackDragTest.rng(0, 100), 1000)

    timeline.split(t0, 40)

    assertEquals(TrackDragTest.rng(0, 40), t0.getRange(s))
    assertSame(s, segAt(t0, 20))
    assertEquals(1000, t0.getOrigin(s))
    val right: Source[?] = segAt(t0, 60)
    assertNotSame(s, right)
    assertEquals(TrackDragTest.rng(40, 100), t0.getRange(right))
    assertSame(t0, timeline.findTrackOf(right))
    // 右半的 origin 与左半一致，源内时间是绝对时间减 origin，两半才接得上
    assertEquals(1000, t0.getOrigin(right))
    assertEquals(2, countOn(t0))
  }

  @Test
  def undoRedoMoveSegsCommandRestoresState(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val s: Source[?] = newSrc(100)
    placeAt(t0, s, TrackDragTest.rng(0, 100), 1000)

    val entries = new java.util.ArrayList[UndoManager.TrackEdit]()
    entries.add(UndoManager.TrackEdit(0, t0, t0.remove(s)))
    entries.add(UndoManager.TrackEdit(1, t1, t1.addOrThrow(s, TrackDragTest.rng(50, 150), 1050)))
    project.undoManager.execute(new UndoManager.MoveSegsCommand(timeline, entries))

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
  def undoRedoResizeSegsCommandRestoresState(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
    place(t0, s, TrackDragTest.rng(0, 100))

    val before = t0
    val entries = new java.util.ArrayList[UndoManager.TrackEdit]()
    entries.add(UndoManager.TrackEdit(0, before,
      before.remove(s).addOrThrow(s, TrackDragTest.rng(30, 100), 0L)))
    project.undoManager.execute(new UndoManager.ResizeSegsCommand(timeline, entries))
    assertEquals(TrackDragTest.rng(30, 100), t0.getRange(s))

    project.undoManager.undo()
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))

    project.undoManager.redo()
    assertEquals(TrackDragTest.rng(30, 100), t0.getRange(s))
  }

  @Test
  def undoRedoSplitSegCommandRestoresState(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    val s: Source[?] = newSrc(100)
    place(t0, s, TrackDragTest.rng(0, 100))
    val before = t0
    val after = before.split(40)
    val right: Source[?] = segAt(after, 60)

    project.undoManager.execute(new UndoManager.SplitSegCommand(timeline, 0, before, after))

    // execute 已应用分割，一分为二
    assertEquals(TrackDragTest.rng(0, 40), t0.getRange(s))
    assertEquals(TrackDragTest.rng(40, 100), t0.getRange(right))
    assertEquals(2, countOn(t0))

    project.undoManager.undo()
    assertEquals(TrackDragTest.rng(0, 100), t0.getRange(s))
    assertEquals(1, countOn(t0))
    assertSame(s, segAt(t0, 50))
  }

  @Test
  def compoundMoveUndoRestoresMultipleSources(): Unit = {
    def t0: Track = timeline.getTrackOrCreate(0)
    def t1: Track = timeline.getTrackOrCreate(1)
    val a: Source[?] = newSrc(100)
    placeAt(t0, a, TrackDragTest.rng(0, 100), 1000)
    val b: Source[?] = newSrc(100)
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

  private def countOn(track: Track): Int = {
    var c = 0
    for (element <- track.asScala) {
      element match {
        case _: Source[?] => c += 1
        case _: Gap =>
      }
    }
    c
  }
}

object TrackDragTest {
  private def rng(lo: Long, hi: Long): Interval = {
    Interval(lo, hi)
  }
}
