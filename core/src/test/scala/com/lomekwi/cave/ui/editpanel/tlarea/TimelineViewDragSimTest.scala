package com.lomekwi.cave.ui.editpanel.tlarea

import com.lomekwi.cave.app.selection.SegmentSet
import com.lomekwi.cave.pipeline.{Segment, Transition}
import com.lomekwi.cave.project.TestProject
import com.lomekwi.cave.timeline.GdxTestBase
import com.lomekwi.cave.timeline.~~
import com.lomekwi.cave.timeline.TestCont
import com.lomekwi.cave.timeline.Timeline
import com.lomekwi.cave.timeline.Track

import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull, assertNull, assertTrue}

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.objenesis.ObjenesisStd

import java.lang.reflect.Field

import TimelineViewDragSimTest.*

/**
 * 用真实 TlSegmentActor 拖拽会话模拟"鼠标拖拽"交互，验证 UI 拖拽逻辑。
 * 1) 小幅拖拽不应被放大成大幅移动；
 * 2) 拖拽边缘裁切时不该改变片段内偏移 origin；
 * 3) act() 重建是模型的纯投影，重建不得改动模型，模型状态必须稳定。
 *
 * TimelineView 的字段初始化会创建 vis-ui 菜单组件（需已加载 Skin），
 * 在 headless 测试里不便构造。因此这里用 Objenesis 绕过构造函数实例化，
 * 再反射填入拖拽逻辑真正依赖的模型/视图字段，其余 UI 保持空。
 * actor 不挂 parent（Group 内部 children 未初始化），直接注入 TimelineView 引用，
 * 拖拽逻辑只读 actor 的位置/尺寸。
 */
class TimelineViewDragSimTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: Timeline = null
  private var tl: TimelineView = null
  private var view: TimelineView.ViewState = null

  @BeforeEach
  def setUp(): Unit = {
    project = new TestProject()
    timeline = project.timeline

    tl = new ObjenesisStd().newInstance(classOf[TimelineView])
    tl.setSize(WIDTH, HEIGHT)

    setField(tl, "timeline", timeline)
    setField(tl, "project", project)
    setField(tl, "selectedSegments", new SegmentSet(timeline))
    setField(tl, "dirty", true)

    view = new TimelineView.ViewState()
    view.startTime = 0
    view.durationTime = DURATION_US
    view.trackHeight = TRACK_H
    view.trackYShift = 0
    setField(tl, "view", view)
  }

  private def newSegment(duration: Long): Segment = {
    new TestCont(duration)
  }

  private def absX(time: Long): Float = {
    view.timeToX(time, tl.getWidth)
  }

  private def trackTopY(index: Int): Float = {
    tl.getHeight + view.trackYShift - (index + 1) * view.trackHeight
  }

  /** 在模型上放置一个片段，并按 act() 的重建逻辑摆好 Actor。 */
  private def place(track: Track, segment: Segment, start: Long, end: Long, origin: Long): TlSegmentActor = {
    timeline.tryAdd(track, segment, start ~~ end, origin)
    val actor = segment.getTlSegmentActor
    actor.tl = tl
    actor.setPosition(absX(start), trackTopY(track.index))
    actor.setSize(absX(end) - absX(start), view.trackHeight)
    actor
  }

  /** 模拟 act() 重建，actor 纯粹按模型摆位，不得改动模型。 */
  private def rebuildFromModel(actor: TlSegmentActor): Unit = {
    val segment = actor.segment
    val track = timeline.findTrackOf(segment)
    val r = track.getRange(segment)
    actor.setPosition(absX(r.lo), trackTopY(track.index))
    actor.setSize(absX(r.hi) - absX(r.lo), view.trackHeight)
  }

  // 中部整体移动，小幅拖拽不应被放大

  @Test
  def middleDragMovesBySameDeltaAndIsStableAndUndoable(): Unit = {
    def t0 = timeline.getTrackOrCreate(0)
    val s = newSegment(1000_000L) // 时长 1s
    val actor = place(t0, s, 0, 1000_000L, 5_000_000L)

    val firstX = actor.getWidth / 2
    val firstY = view.trackHeight / 2
    actor.dragSide = DragSide.MIDDLE
    actor.initDrag(firstX, firstY)

    val mouseLocalX = actor.getX + firstX + 100f // 右移 100px
    val mouseLocalY = actor.getY + firstY

    // 事件驱动一次，应恰好移动 100_000µs
    actor.dragTo(mouseLocalX, mouseLocalY)
    assertEquals(100_000L ~~ (1000_000L + 100_000L), t0.getRange(s))

    // 鼠标不动，多帧重建（纯投影），模型与 origin 必须稳定
    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(100_000L ~~ (1000_000L + 100_000L), t0.getRange(s))
      assertEquals(5_000_000L + 100_000L, t0.getOrigin(s))
      i += 1
    }

    actor.finishDrag()

    project.undoManager.undo()
    assertEquals(0L ~~ 1000_000L, t0.getRange(s))
    assertEquals(5_000_000L, t0.getOrigin(s))
  }

  // 竖直方向整个移动，拖动一小段距离不应跳到极远轨道

  @Test
  def middleDragVerticalLandsOnMouseTrackAndIsStable(): Unit = {
    // 用 yToTrackIndex 反推，鼠标停在轨道 2 的带内（mouseLocalY≈200 → index 2）
    def t0 = timeline.getTrackOrCreate(0)
    timeline.getTrackOrCreate(3) // 确保轨道存在
    val s = newSegment(1000_000L)
    val actor = place(t0, s, 0, 1000_000L, 0L)

    val firstX = actor.getWidth / 2
    val firstY = view.trackHeight / 2
    actor.dragSide = DragSide.MIDDLE
    actor.initDrag(firstX, firstY)

    // 目标的 yToTrackIndex(targetY + trackHeight/2) == 2
    val mouseLocalY = 200f
    val mouseLocalX = actor.getX + firstX
    actor.dragTo(mouseLocalX, mouseLocalY)

    // 应恰好落到轨道 2，而不是越跳越远
    assertEquals(2, timeline.findTrackOf(s).index)

    // 鼠标不动，多帧重建（纯投影），轨道必须稳定
    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(2, timeline.findTrackOf(s).index)
      i += 1
    }
  }

  // 边缘裁切，小幅拖拽不应放大，且不改变 origin

  @Test
  def frontResizeMovesStartBySameDeltaKeepsOriginAndIsStable(): Unit = {
    def t0 = timeline.getTrackOrCreate(0)
    val s = newSegment(1000_000L)
    val actor = place(t0, s, 0, 1000_000L, 0L)

    actor.dragSide = DragSide.FRONT
    actor.initDrag(5f, view.trackHeight / 2)

    val newFrontLocalX = 50f // 起点右移 50px=50ms
    actor.dragTo(newFrontLocalX, view.trackHeight / 2)
    assertEquals(50_000L ~~ 1000_000L, t0.getRange(s))
    assertEquals(0, t0.getOrigin(s))

    // 鼠标不动（停在裁切后的新起点 absX(50000)），多帧重建，起点与 origin 都必须稳定
    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(50_000L ~~ 1000_000L, t0.getRange(s))
      assertEquals(0, t0.getOrigin(s))
      i += 1
    }
  }

  @Test
  def behindResizeMovesEndBySameDeltaKeepsOriginAndIsStable(): Unit = {
    def t0 = timeline.getTrackOrCreate(0)
    val s = newSegment(1000_000L)
    val actor = place(t0, s, 0, 1000_000L, 5_000_000L)

    actor.dragSide = DragSide.BEHIND
    actor.initDrag(actor.getWidth, view.trackHeight / 2)

    val newWidth = actor.getWidth + 60f // 终点右移 60px=60ms
    actor.dragTo(newWidth, view.trackHeight / 2)
    assertEquals(0L ~~ (1000_000L + 60_000L), t0.getRange(s))
    assertEquals(5_000_000L, t0.getOrigin(s))

    // 鼠标不动，多帧重建，终点与 origin 都必须稳定
    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(0L ~~ (1000_000L + 60_000L), t0.getRange(s))
      assertEquals(5_000_000L, t0.getOrigin(s))
      i += 1
    }
  }

  // 多选含转场：转场作为成员与两侧内容一起平移，多帧拖拽必须稳定

  @Test
  def middleDragWithTransitionMemberStaysStable(): Unit = {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(2000_000L)
    val b = newSegment(2000_000L)
    // a 与 b 重叠建转场，得 a=[0,1000000) 转场=[500000,1000000) b=[500000,1500000)
    val aActor = place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    rebuildFromModel(aActor)

    val transition: Segment = t0.get(500_000L) match {
      case s: Segment => s
      case _ => null
    }
    assertTrue(transition.isInstanceOf[Transition])

    // 锚点是内容 a，转场作为同轨成员一起选中
    tl.selectedSegments.add(a)
    tl.selectedSegments.add(transition)

    val firstX = aActor.getWidth / 2
    val firstY = view.trackHeight / 2
    aActor.dragSide = DragSide.MIDDLE
    aActor.initDrag(firstX, firstY)

    // 鼠标停在按下点右侧 100px（100ms）
    val mouseLocalX = aActor.getX + firstX + 100f
    val mouseLocalY = aActor.getY + firstY
    aActor.dragTo(mouseLocalX, mouseLocalY)

    // 转场仍然在轨道上，没有被成员平移连带摘除
    assertNotNull(timeline.findTrackOf(transition))

    // 鼠标不动，后续多帧重建与拖拽都不应崩溃
    var i = 0
    while (i < 5) {
      rebuildFromModel(aActor)
      aActor.dragTo(mouseLocalX, mouseLocalY)
      assertNotNull(timeline.findTrackOf(a))
      i += 1
    }

    aActor.finishDrag()
    project.undoManager.undo()
    assertEquals(0L ~~ 1000_000L, t0.getRange(a))
    assertNotNull(timeline.findTrackOf(transition))
  }

  // 锚点是转场时，MIDDLE 拖拽把转场当作整体平移，多帧不得崩溃

  @Test
  def middleDragWithTransitionAnchorStaysStable(): Unit = {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(2000_000L)
    val b = newSegment(2000_000L)
    place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    // a=[0,1000000) 转场=[500000,1000000) b=[500000,1500000)
    val transition: Segment = t0.get(500_000L) match {
      case s: Segment => s
      case _ => null
    }
    assertTrue(transition.isInstanceOf[Transition])

    // 锚点是转场，b 作为同轨成员一起选中
    tl.selectedSegments.add(transition)
    tl.selectedSegments.add(b)

    val tActor = transition.getTlSegmentActor
    tActor.tl = tl
    rebuildFromModel(tActor)

    val firstX = tActor.getWidth / 2
    val firstY = view.trackHeight / 2
    tActor.dragSide = DragSide.MIDDLE
    tActor.initDrag(firstX, firstY)

    val mouseLocalX = tActor.getX + firstX + 100f
    val mouseLocalY = tActor.getY + firstY
    tActor.dragTo(mouseLocalX, mouseLocalY)

    // 转场作为锚点随组一起平移，仍然在轨道上
    assertNotNull(timeline.findTrackOf(transition))

    // 后续多帧拖拽不得崩溃
    var i = 0
    while (i < 5) {
      tActor.dragTo(mouseLocalX, mouseLocalY)
      i += 1
    }

    tActor.finishDrag()
    project.undoManager.undo()
    assertNotNull(timeline.findTrackOf(transition))
  }

  // 两个片段重叠出转场后，拖其中一个仍应吸附到对方的边缘

  @Test
  def middleDragSnapsToOverlappingNeighbourEdge(): Unit = {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    val aActor = place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    // a 与 b 重叠出转场，得 a=[0,1000000) 转场=[500000,1000000) b=[500000,1500000)
    rebuildFromModel(aActor)

    val firstX = aActor.getWidth / 2
    val firstY = view.trackHeight / 2
    aActor.dragSide = DragSide.MIDDLE
    aActor.initDrag(firstX, firstY)

    // 目标起点落在 b 起点内侧 5px（阈值 10px），应吸到 b 的起点
    aActor.dragTo(absX(495_000L) + firstX, aActor.getY + firstY)

    assertEquals(500_000L, tl.snapIndicatorTime)
    assertEquals(500_000L, t0.getRange(a).lo, 1L)
  }

  // 转场拖到消失后再拖、重建后小幅移动，吸附不得把目标吸回正在移动的边

  @Test
  def behindDragPastTransitionRemovalKeepsFollowing(): Unit = {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    // a=[0,500000) 转场=[500000,1000000) b=[1000000,1500000)
    val transition: Segment = t0.get(500_000L) match {
      case s: Segment => s
      case _ => null
    }
    assertTrue(transition.isInstanceOf[Transition])

    val tActor = transition.getTlSegmentActor
    tActor.tl = tl
    rebuildFromModel(tActor)
    tActor.dragSide = DragSide.BEHIND
    tActor.initDrag(tActor.getWidth - 5f, view.trackHeight / 2)

    // 越过转场左缘，转场消失，a 的可见终点跟手
    tActor.dragTo(absX(400_000L), view.trackHeight / 2)
    assertNull(timeline.findTrackOf(transition))
    assertEquals(0L ~~ 400_000L, t0.getRange(a))

    // 拖回右边重建转场
    tActor.dragTo(absX(900_000L), view.trackHeight / 2)
    val rebuilt: Segment = t0.get(600_000L) match {
      case s: Segment => s
      case _ => null
    }
    assertTrue(rebuilt.isInstanceOf[Transition])
    assertEquals(500_000L ~~ 900_000L, t0.getRange(rebuilt))

    // 小幅左移（在吸附阈值内），目标不得被吸回转场右缘
    tActor.dragTo(absX(896_000L), view.trackHeight / 2)
    assertEquals(896_000L, t0.getRange(a).hi)
    assertEquals(500_000L ~~ 896_000L, t0.getRange(rebuilt))
  }

  // 拖转场左缘越过右缘，转场应消失，右侧内容起点继续跟手

  @Test
  def frontDragOverTransitionRemovesItAndKeepsFollowing(): Unit = {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    val aActor = place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    // 重叠建转场，得 a=[0,1000000) 转场=[500000,1000000) b=[500000,1500000)
    assertEquals(0L ~~ 1000_000L, t0.getRange(a))
    assertEquals(500_000L ~~ 1500_000L, t0.getRange(b))
    rebuildFromModel(aActor)

    val transition: Segment = t0.get(500_000L) match {
      case s: Segment => s
      case _ => null
    }
    assertTrue(transition.isInstanceOf[Transition])

    val tActor = transition.getTlSegmentActor
    tActor.tl = tl
    rebuildFromModel(tActor)

    // 按转场左缘，向右拖把 b 的可见起点推过转场终点
    tActor.dragSide = DragSide.FRONT
    tActor.initDrag(5f, view.trackHeight / 2)
    val mouseLocalX = absX(1200_000L)
    tActor.dragTo(mouseLocalX, view.trackHeight / 2)

    // 转场消失，a 恢复完整，b 的起点越过转场终点继续跟手
    assertNull(timeline.findTrackOf(transition))
    assertEquals(0L ~~ 1000_000L, t0.getRange(a))
    val bRange = t0.getRange(b)
    assertEquals(1500_000L, bRange.hi)
    assertTrue(Math.abs(bRange.lo - 1200_000L) <= 1000L, "b 起点应跟手到约 1200000，实际 " + bRange.lo)

    tActor.finishDrag()
    project.undoManager.undo()
    assertEquals(0L ~~ 1000_000L, t0.getRange(a))
    assertEquals(500_000L ~~ 1500_000L, t0.getRange(b))
    assertNotNull(timeline.findTrackOf(transition))
  }

  // 越过消失点后再拖回，转场应重建

  @Test
  def frontDragBackOverGapRebuildsTransition(): Unit = {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    val aActor = place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    rebuildFromModel(aActor)

    val transition: Segment = t0.get(500_000L) match {
      case s: Segment => s
      case _ => null
    }
    val tActor = transition.getTlSegmentActor
    tActor.tl = tl
    rebuildFromModel(tActor)

    tActor.dragSide = DragSide.FRONT
    tActor.initDrag(5f, view.trackHeight / 2)
    tActor.dragTo(absX(1200_000L), view.trackHeight / 2)
    assertNull(timeline.findTrackOf(transition))

    // 拖回 a 的终点之内，重叠重新成为转场
    tActor.dragTo(absX(900_000L), view.trackHeight / 2)
    assertTrue(t0.get(900_000L).isInstanceOf[Transition])
    assertEquals(0L ~~ 1000_000L, t0.getRange(a))
    assertEquals(900_000L ~~ 1500_000L, t0.getRange(b))
  }

  // 拖转场右缘越过左缘，转场应消失，左侧内容终点继续跟手

  @Test
  def behindDragOverTransitionRemovesItAndKeepsFollowing(): Unit = {
    def t0 = timeline.getTrackOrCreate(0)
    val a = newSegment(1000_000L)
    val b = newSegment(1000_000L)
    val aActor = place(t0, a, 0, 1000_000L, 0L)
    place(t0, b, 500_000L, 1500_000L, 500_000L)
    rebuildFromModel(aActor)

    val transition: Segment = t0.get(500_000L) match {
      case s: Segment => s
      case _ => null
    }
    val tActor = transition.getTlSegmentActor
    tActor.tl = tl
    rebuildFromModel(tActor)

    // 按转场右缘，向左拖把 a 的可见终点推过转场起点
    tActor.dragSide = DragSide.BEHIND
    tActor.initDrag(tActor.getWidth, view.trackHeight / 2)
    tActor.dragTo(absX(300_000L), view.trackHeight / 2)

    assertNull(timeline.findTrackOf(transition))
    assertEquals(0L ~~ 300_000L, t0.getRange(a))
    assertEquals(500_000L ~~ 1500_000L, t0.getRange(b))
  }
}

object TimelineViewDragSimTest {

  private final val WIDTH: Float = 2000f
  private final val HEIGHT: Float = 400f
  private final val TRACK_H: Float = 80f
  // durationTime 单位为微秒（µs），这里 1px = 1000µs = 1ms
  private final val DURATION_US: Long = 2_000_000L

  private def setField(target: Object, name: String, value: Any): Unit = {
    val f: Field = target.getClass.getDeclaredField(name)
    f.setAccessible(true)
    f.set(target, value.asInstanceOf[AnyRef])
  }
}
