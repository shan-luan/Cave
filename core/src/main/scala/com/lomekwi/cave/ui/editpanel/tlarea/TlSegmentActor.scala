package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.{Application, Gdx, Input}
import com.badlogic.gdx.graphics.{Color, Cursor}
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.{Actor, InputEvent, InputListener}
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.lomekwi.cave.pipeline.{Gap, Segment}
import com.lomekwi.cave.timeline.{Interval, SegmentGroup, Track}

import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.Colors


import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import java.util

/** 时间线上单个片段的可视化表示与交互入口。 */
abstract class TlSegmentActor(private val segment: Segment[?]) extends Actor {
  private[tlarea] var tl: TimelineView = uninitialized
  private[tlarea] var dragSide: DragSide = DragSide.NONE

  private[tlarea] var firstX: Float = Float.NaN
  private[tlarea] var firstY: Float = Float.NaN
  private var dragOldStart: Long = 0L
  private var dragOldDuration: Long = 0L
  private var dragMembers: util.List[Segment[?]] = uninitialized
  private var dragOrigStarts: Array[Long] = uninitialized
  private var dragOrigDurations: Array[Long] = uninitialized

  private val scissors: Rectangle = new Rectangle()
  private val bounds: Rectangle = new Rectangle()
  private var hovered: Boolean = false
  private var menuInitialized: Boolean = false

  /** 本片段当前所在的轨道；未在时间线上时为空。 */
  private[tlarea] def track: Track = {
    if (tl == null) null else tl.timeline.findTrackOf(segment)
  }

  /** 本片段占用的时间区间；未在时间线上时为空。 */
  private[tlarea] def range: Interval = {
    val t = track
    if (t == null) null else t.getRange(segment)
  }

  /** 片段的 0 秒在时间轴中的位置；未在时间线上时为 0。 */
  private[tlarea] def origin: Long = {
    val t = track
    if (t == null) 0L else t.getOrigin(segment)
  }

  /** 本片段所属的组；不属于任何组时为空。 */
  private[tlarea] def group: SegmentGroup = {
    if (tl == null) null else tl.timeline.getGroup(segment)
  }

  addListener(new InputListener {
    final val edgeWidth: Float = 30

    override def mouseMoved(event: InputEvent, x: Float, y: Float): Boolean = {
      hovered = true
      if (x < edgeWidth) {
        setCursor(Cursor.SystemCursor.HorizontalResize)
      } else if (x > getWidth - edgeWidth) {
        setCursor(Cursor.SystemCursor.HorizontalResize)
      } else {
        setCursor(Cursor.SystemCursor.AllResize)
      }
      val g: SegmentGroup = group
      if (g != null) {
        for (s <- g.asScala) {
          if (s != segment) {
            s.getTlSegmentActor.setHovered(true)
          }
        }
      }
      false
    }

    override def exit(event: InputEvent, x: Float, y: Float, pointer: Int, toActor: Actor): Unit = {
      hovered = false
      if (dragSide == DragSide.NONE) {
        setCursor(Cursor.SystemCursor.Arrow)
      }
      val g: SegmentGroup = group
      if (g != null) {
        for (s <- g.asScala) {
          if (s != segment) {
            s.getTlSegmentActor.setHovered(false)
          }
        }
      }
    }

    override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
      val parent = getParent
      if (parent == null || !parent.isInstanceOf[TimelineView]) {
        return false
      }
      val timelineView = parent.asInstanceOf[TimelineView]
      if (button == Input.Buttons.LEFT) {
        if (x < edgeWidth) {
          dragSide = DragSide.FRONT
        } else if (x > getWidth - edgeWidth) {
          dragSide = DragSide.BEHIND
        } else {
          dragSide = DragSide.MIDDLE
        }
        event.stop()
        val alreadySelected: Boolean = timelineView.selectedSegments.contains(segment)
        if (!alreadySelected) {
          timelineView.selectSegment(segment, false)
        }
        tl = timelineView
        initDrag(x, y)
        true
      } else {
        getMenu.setContext(TlSegmentActor.this, parent.asInstanceOf[TimelineView].xToAbsoluteTime(getX + x))
        false
      }
    }

    override def touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
      dragTo(x, y)
    }

    override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
      finishDrag()
      dragSide = DragSide.NONE
      setCursor(Cursor.SystemCursor.Arrow)
    }
  })

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    if (range == null) return
    ScissorStack.calculateScissors(App.root.getStage.getCamera, batch.getTransformMatrix, bounds, scissors)
    if (ScissorStack.pushScissors(scissors)) {
      var visibleStartX: Float = 0
      var visibleEndX: Float = getWidth
      if (getParent != null) {
        val parentW: Float = getParent.getWidth
        visibleStartX = Math.max(0f, -getX)
        visibleEndX = Math.min(getWidth, parentW - getX)
      }
      drawContent(batch, parentAlpha, visibleStartX, visibleEndX)
      drawBorder()
      drawSelectionOverlay()
      batch.flush()
      ScissorStack.popScissors()
    }
  }

  protected def drawContent(batch: Batch, parentAlpha: Float, visibleStartX: Float, visibleEndX: Float): Unit = {
    App.root.getShapeDrawer.filledRectangle(getX, getY, getWidth, getHeight, Colors.ACCENT_LIGHT)
  }

  private def drawBorder(): Unit = {
    val s = isSelected
    App.root.getShapeDrawer.rectangle(getX, getY, getWidth, getHeight, if (s) Color.WHITE else Colors.ACCENT, if (s) 6f else 2f)
  }

  private def drawSelectionOverlay(): Unit = {
    if (hovered) {
      App.root.getShapeDrawer.filledRectangle(getX, getY, getWidth, getHeight, Colors.SRC_HOVER)
    }
  }

  private def setHovered(hovered: Boolean): Unit = {
    this.hovered = hovered
  }

  def getSegment: Segment[?] = {
    segment
  }

  /** 选中态由视图持有，actor 只是查询者。 */
  private def isSelected: Boolean = {
    tl != null && tl.selectedSegments.contains(segment)
  }

  def getDragSide: DragSide = {
    dragSide
  }

  // 拖拽会话由本 actor 驱动，actor 位置是模型的纯投影。

  /** 按下时调用，快照参与拖拽的成员并开始录制 undo。 */
  private[tlarea] def initDrag(diffToActorX: Float, diffToActorY: Float): Unit = {
    val r = range
    dragOldStart = r.lo
    dragOldDuration = r.hi - dragOldStart
    firstX = diffToActorX
    firstY = diffToActorY
    tl.timeline.record()
    initDragMembers()
  }

  /** 拖拽中，每次鼠标移动都会调用，按 dragSide 分派到三种分支（含吸附）。 */
  private[tlarea] def dragTo(diffToActorX: Float, diffToActorY: Float): Unit = {
    if (tl == null || dragSide == DragSide.NONE) return

    tl.snapIndicatorTime = -1
    val r = range

    dragSide match {
      case DragSide.FRONT =>
        // target = 鼠标 stage x（diffToActorX 与 getX() 相消）
        var target: Float = getX + diffToActorX
        target = Math.max(target, tl.absoluteTimeToX(0))
        handleFrontResize(snapResizeTime(Math.max(tl.xToAbsoluteTime(target), 0)))
      case DragSide.BEHIND =>
        val upper: Float = getX + diffToActorX
        val rawUpper: Long = Math.max(tl.xToAbsoluteTime(upper), 0)
        handleBehindResize(snapResizeTime(rawUpper))
      case DragSide.MIDDLE =>
        // deltaX/deltaY 为相对按下点的累计位移，同帧多次 mouse move 不会重复累加
        val deltaX: Float = diffToActorX - firstX
        val deltaY: Float = diffToActorY - firstY
        val targetX: Float = getX + deltaX
        val targetY: Float = getY + deltaY

        val duration: Long = r.hi - r.lo
        var target: Long = tl.xToAbsoluteTime(targetX)
        if (target < 0) target = 0
        target = snapMoveTarget(target, duration)

        val newTrack: Track = tl.timeline.getTrackOrCreate(Math.max(0, tl.yToTrackIndex(targetY + tl.view.trackHeight / 2)))

        handleMiddleDrag(target, newTrack)
      case _ => ()
    }

    tl.dirty = true
  }

  // 吸附点由 Timeline.snapTime 获取，这里只做阈值换算、忽略集与指示线。

  private def snapThreshold(): Long = {
    Math.max(1, (TlSegmentActor.SNAP_THRESHOLD_PX / tl.getWidth * tl.view.durationTime).toLong)
  }

  private def snapDisabled(): Boolean = {
    App.shortcutManager.isActive(TimelineView.Actions.SNAP_IGNORE)
  }

  /** 裁切吸附，忽略 drag 成员与同轨道片段，返回吸附后的时间并设置指示线。 */
  private def snapResizeTime(rawTime: Long): Long = {
    if (snapDisabled()) {
      rawTime
    } else {
      val ignore: util.Set[Segment[?]] = new util.HashSet[Segment[?]](dragMembers)
      val t = track
      if (t != null) {
        for (element <- t.asScala) {
          element match {
            case s: Segment[?] => ignore.add(s)
            case _: Gap =>
          }
        }
      }
      val snapped: Long = tl.timeline.snapTime(rawTime, snapThreshold(), ignore)
      if (snapped != rawTime) tl.snapIndicatorTime = snapped
      snapped
    }
  }

  /** 整体移动吸附，起点与终点各求吸附点，取更近者。 */
  private def snapMoveTarget(target: Long, duration: Long): Long = {
    if (snapDisabled()) {
      target
    } else {
      val ignore: util.Set[Segment[?]] = new util.HashSet[Segment[?]](dragMembers)
      val srcEnd: Long = target + duration
      val snappedStart: Long = tl.timeline.snapTime(target, snapThreshold(), ignore)
      var snappedEnd: Long = tl.timeline.snapTime(srcEnd, snapThreshold(), ignore) - duration
      if (snappedEnd < 0) snappedEnd = 0
      val startMoved: Boolean = snappedStart != target
      val endMoved: Boolean = snappedEnd != target
      if (startMoved && endMoved) {
        if (Math.abs(snappedStart - target) <= Math.abs(snappedEnd - target)) {
          tl.snapIndicatorTime = snappedStart
          snappedStart
        } else {
          tl.snapIndicatorTime = snappedEnd + duration
          snappedEnd
        }
      } else if (startMoved) {
        tl.snapIndicatorTime = snappedStart
        snappedStart
      } else if (endMoved) {
        tl.snapIndicatorTime = snappedEnd + duration
        snappedEnd
      } else {
        target
      }
    }
  }

  /** 松手时调用，提交 undo 并清空会话。 */
  private[tlarea] def finishDrag(): Unit = {
    if (tl != null) {
      tl.dirty = true
      tl.snapIndicatorTime = -1
      tl.timeline.submit()
      dragMembers = null
      dragOrigStarts = null
      dragOrigDurations = null
    }
  }

  /** 收集参与拖拽的成员并快照各自的起点/时长。 */
  private def initDragMembers(): Unit = {
    val selected = tl.selectedSegments
    if (selected.size() > 1 && selected.contains(segment)) {
      dragMembers = new util.ArrayList[Segment[?]](selected.size())
      dragMembers.add(segment)
      for (s <- selected.asScala) {
        if (s != segment) dragMembers.add(s)
      }
    } else {
      dragMembers = new util.ArrayList[Segment[?]](1)
      dragMembers.add(segment)
    }

    val n: Int = dragMembers.size()
    dragOrigStarts = new Array[Long](n)
    dragOrigDurations = new Array[Long](n)
    for (i <- 0 until n) {
      val member = dragMembers.get(i)
      val memberTrack = tl.timeline.findTrackOf(member)
      val r = memberTrack.getRange(member)
      dragOrigStarts(i) = r.lo
      dragOrigDurations(i) = r.hi - r.lo
    }
  }

  private def handleMiddleDrag(target: Long, newTrack: Track): Unit = {
    val members: util.List[Segment[?]] = util.List.copyOf(dragMembers)

    val firstTrack: Track = tl.timeline.findTrackOf(members.get(0))
    val trackDelta: Int = newTrack.index - firstTrack.index

    val minIdx: Int = members.stream().mapToInt((m: Segment[?]) => tl.timeline.findTrackOf(m).index).min().orElseThrow()
    if (minIdx + trackDelta >= 0) {
      val currentStart0: Long = firstTrack.getRange(members.get(0)).lo
      tl.timeline.moveTime(members, target - currentStart0)

      if (trackDelta != 0) {
        tl.timeline.moveTrack(members, trackDelta)
      }
    }
  }

  private def handleFrontResize(newStart: Long): Unit = {
    val absDelta: Long = newStart - dragOldStart
    val n: Int = dragMembers.size()
    val inBounds = (0 until n).forall { i =>
      val ns: Long = dragOrigStarts(i) + absDelta
      ns < dragOrigStarts(i) + dragOrigDurations(i) && ns >= 0
    }
    if (inBounds) {
      val members: util.List[Segment[?]] = util.List.copyOf(dragMembers)
      val currentStart0: Long = tl.timeline.findTrackOf(members.get(0)).getRange(members.get(0)).lo

      tl.timeline.setStart(members, newStart - currentStart0)
    }
  }

  private def handleBehindResize(newEnd: Long): Unit = {
    val oldEnd: Long = dragOldStart + dragOldDuration
    val absDelta: Long = newEnd - oldEnd
    val n: Int = dragMembers.size()
    val inBounds = (0 until n).forall { i =>
      val ne: Long = dragOrigStarts(i) + dragOrigDurations(i) + absDelta
      ne > dragOrigStarts(i)
    }
    if (inBounds) {
      val members: util.List[Segment[?]] = util.List.copyOf(dragMembers)
      val currentEnd0: Long = tl.timeline.findTrackOf(members.get(0)).getRange(members.get(0)).hi

      tl.timeline.setEnd(members, newEnd - currentEnd0)
    }
  }

  private def setCursor(cursor: Cursor.SystemCursor): Unit = {
    if (Gdx.app.getType == Application.ApplicationType.Desktop) {
      Gdx.graphics.setSystemCursor(cursor)
    }
  }

  private def getMenu: TlSegmentMenu = {
    getParent match {
      case g: TimelineView => g.srcMenu
      case _ => null
    }
  }

  private[tlarea] def initMenu(): Unit = {
    if (!menuInitialized) {
      val menu: TlSegmentMenu = getMenu
      if (menu != null) {
        addListener(menu.getDefaultInputListener)
        menuInitialized = true
      }
    }
  }

  override protected def positionChanged(): Unit = {
    bounds.set(getX, getY, getWidth, getHeight)
  }

  override protected def sizeChanged(): Unit = {
    bounds.set(getX, getY, getWidth, getHeight)
  }
}

object TlSegmentActor {
  private final val SNAP_THRESHOLD_PX: Float = 10f
}
