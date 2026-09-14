package com.lomekwi.cave.ui.editpanel.tlarea

import com.google.common.collect.Range
import com.lomekwi.cave.project.TestProject
import com.lomekwi.cave.timeline.GdxTestBase
import com.lomekwi.cave.timeline.Segment
import com.lomekwi.cave.timeline.SegmentSet
import com.lomekwi.cave.timeline.TestSource
import com.lomekwi.cave.timeline.Timeline
import com.lomekwi.cave.timeline.Track

import org.junit.Assert.assertEquals

import org.junit.Before
import org.junit.Test
import org.objenesis.ObjenesisStd

import java.lang.reflect.Field

import TlGroupDragSimTest.*

/**
 * 用真实 SegActor 拖拽会话模拟"鼠标拖拽"交互，验证重构后的 UI 拖拽逻辑：
 * 1) 小幅拖拽不应被放大成大幅移动；
 * 2) 拖拽边缘裁切时不该改变片段 origin；
 * 3) act() 重建是模型的纯投影：重建不得改动模型，模型状态必须稳定。
 *
 * 说明：TlGroup 的字段初始化会创建 vis-ui 菜单组件（需已加载 Skin），
 * 在 headless 测试里不便构造。因此这里用 Objenesis 绕过构造函数实例化，
 * 再反射填入拖拽逻辑真正依赖的模型/视图字段，其余 UI 保持空。
 * actor 不挂 parent（Group 内部 children 未初始化），直接注入 TlGroup 引用，
 * 拖拽逻辑只读 actor 的位置/尺寸。
 */
class TlGroupDragSimTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: Timeline = null
  private var tl: TlGroup = null
  private var view: TlGroup.ViewState = null

  @Before
  def setUp(): Unit = {
    project = new TestProject()
    timeline = project.timeline

    tl = new ObjenesisStd().newInstance(classOf[TlGroup])
    tl.setSize(WIDTH, HEIGHT)

    setField(tl, "timeline", timeline)
    setField(tl, "project", project)
    setField(tl, "selectedSegments", new SegmentSet())
    setField(tl, "dirty", true)

    view = new TlGroup.ViewState()
    view.startTime = 0
    view.durationTime = DURATION_US
    view.trackHeight = TRACK_H
    view.trackYShift = 0
    setField(tl, "view", view)
  }

  private def newSeg(duration: Long): Segment = {
    new Segment(new TestSource(duration))
  }

  private def absX(time: Long): Float = {
    view.timeToX(time, tl.getWidth)
  }

  private def trackTopY(index: Int): Float = {
    tl.getHeight + view.trackYShift - (index + 1) * view.trackHeight
  }

  /** 在模型上放置一个片段，并按 act() 的重建逻辑摆好 Actor。 */
  private def place(track: Track, s: Segment, start: Long, end: Long): SegActor = {
    timeline.tryAdd(track, s, Range.closedOpen(start, end))
    val actor = s.getActor()
    actor.tl = tl
    actor.setPosition(absX(start), trackTopY(track.index))
    actor.setSize(absX(end) - absX(start), view.trackHeight)
    actor
  }

  /** 模拟 act() 重建：actor 纯粹按模型摆位，不得改动模型。 */
  private def rebuildFromModel(actor: SegActor): Unit = {
    val s = actor.getSegment()
    val r = s.getRange()
    actor.setPosition(absX(r.lowerEndpoint()), trackTopY(s.getTrack().index))
    actor.setSize(absX(r.upperEndpoint()) - absX(r.lowerEndpoint()), view.trackHeight)
  }

  // ---------------------------------------------------------------------
  // 中部整体移动：小幅拖拽不应被放大
  // ---------------------------------------------------------------------

  @Test
  def middleDragMovesBySameDeltaAndIsStableAndUndoable(): Unit = {
    val t0 = timeline.getTrack(0)
    val s = newSeg(1000_000L) // 时长 1s
    s.setOrigin(5_000_000L)
    val actor = place(t0, s, 0, 1000_000L)

    val firstX = actor.getWidth / 2
    val firstY = view.trackHeight / 2
    actor.dragSide = DragSide.MIDDLE
    actor.initDrag(firstX, firstY)

    val mouseLocalX = actor.getX + firstX + 100f // 右移 100px
    val mouseLocalY = actor.getY + firstY

    // 事件驱动一次：应恰好移动 100_000µs
    actor.dragTo(mouseLocalX - actor.getX, mouseLocalY - actor.getY)
    assertEquals(Range.closedOpen(100_000L, 1000_000L + 100_000L), s.getRange())

    // 鼠标不动，多帧重建（纯投影）：模型与 origin 必须稳定
    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(Range.closedOpen(100_000L, 1000_000L + 100_000L), s.getRange())
      assertEquals(5_000_000L + 100_000L, s.getOrigin())
      i += 1
    }

    actor.finishDrag()

    project.undoManager.undo()
    assertEquals(Range.closedOpen(0L, 1000_000L), s.getRange())
    assertEquals(5_000_000L, s.getOrigin())
  }

  // ---------------------------------------------------------------------
  // 竖直方向整个移动：拖动一小段距离不应跳到极远轨道
  // ---------------------------------------------------------------------

  @Test
  def middleDragVerticalLandsOnMouseTrackAndIsStable(): Unit = {
    // 用 yToTrackIndex 反推：鼠标停在轨道 2 的带内（mouseLocalY≈200 → index 2）
    val t0 = timeline.getTrack(0)
    timeline.getTrack(3) // 确保轨道存在
    val s = newSeg(1000_000L)
    val actor = place(t0, s, 0, 1000_000L)

    val firstX = actor.getWidth / 2
    val firstY = view.trackHeight / 2
    actor.dragSide = DragSide.MIDDLE
    actor.initDrag(firstX, firstY)

    // 目标的 yToTrackIndex(targetY + trackHeight/2) == 2
    val mouseLocalY = 200f
    val mouseLocalX = actor.getX + firstX
    actor.dragTo(mouseLocalX - actor.getX, mouseLocalY - actor.getY)

    // 应恰好落到轨道 2，而不是越跳越远
    assertEquals(2, s.getTrack().index)

    // 鼠标不动，多帧重建（纯投影）：轨道必须稳定
    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(2, s.getTrack().index)
      i += 1
    }
  }

  // ---------------------------------------------------------------------
  // 边缘裁切：小幅拖拽不应放大，且不改变 origin
  // ---------------------------------------------------------------------

  @Test
  def frontResizeMovesStartBySameDeltaKeepsOriginAndIsStable(): Unit = {
    val t0 = timeline.getTrack(0)
    val s = newSeg(1000_000L)
    s.setOrigin(0)
    val actor = place(t0, s, 0, 1000_000L)

    actor.dragSide = DragSide.FRONT
    actor.initDrag(5f, view.trackHeight / 2)

    val newFrontLocalX = 50f // 起点右移 50px=50ms
    actor.dragTo(newFrontLocalX, view.trackHeight / 2)
    assertEquals(Range.closedOpen(50_000L, 1000_000L), s.getRange())
    assertEquals(0, s.getOrigin())

    // 鼠标不动（停在裁切后的新起点 absX(50000)），多帧重建：起点与 origin 都必须稳定
    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(Range.closedOpen(50_000L, 1000_000L), s.getRange())
      assertEquals(0, s.getOrigin())
      i += 1
    }
  }

  @Test
  def behindResizeMovesEndBySameDeltaKeepsOriginAndIsStable(): Unit = {
    val t0 = timeline.getTrack(0)
    val s = newSeg(1000_000L)
    s.setOrigin(5_000_000L)
    val actor = place(t0, s, 0, 1000_000L)

    actor.dragSide = DragSide.BEHIND
    actor.initDrag(actor.getWidth, view.trackHeight / 2)

    val newWidth = actor.getWidth + 60f // 终点右移 60px=60ms
    actor.dragTo(newWidth, view.trackHeight / 2)
    assertEquals(Range.closedOpen(0L, 1000_000L + 60_000L), s.getRange())
    assertEquals(5_000_000L, s.getOrigin())

    // 鼠标不动，多帧重建：终点与 origin 都必须稳定
    var i = 0
    while (i < 5) {
      rebuildFromModel(actor)
      assertEquals(Range.closedOpen(0L, 1000_000L + 60_000L), s.getRange())
      assertEquals(5_000_000L, s.getOrigin())
      i += 1
    }
  }
}

object TlGroupDragSimTest {

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
