package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.{Application, Gdx, Input}
import com.badlogic.gdx.graphics.{Color, Cursor}
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.{Actor, InputEvent, InputListener}
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.lomekwi.cave.timeline.{Segment, SegmentGroup, Track}

import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.Colors


import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import java.util

/** 时间线上单个片段的可视化表示与交互入口。 */
abstract class SegActor(private val segment: Segment) extends Actor {
  private[tlarea] var tl: TlGroup = uninitialized
  private[tlarea] var dragSide: DragSide = DragSide.NONE

  // 拖拽状态 //////////////////////////
  private[tlarea] var firstX: Float = Float.NaN
  private[tlarea] var firstY: Float = Float.NaN
  private var dragOldStart: Long = 0L
  private var dragOldDuration: Long = 0L
  private var dragMembers: util.List[Segment] = uninitialized
  private var dragOrigStarts: Array[Long] = uninitialized
  private var dragOrigDurations: Array[Long] = uninitialized
  private var dragOrigTracks: Array[Track] = uninitialized

  private val scissors: Rectangle = new Rectangle()
  private val bounds: Rectangle = new Rectangle()
  private var hovered: Boolean = false
  private var menuInitialized: Boolean = false

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
      val group: SegmentGroup = segment.getGroup
      if (group != null) {
        for (s <- group.asScala) {
          if (s != segment) {
            s.getActor.setHovered(true)
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
      val group: SegmentGroup = segment.getGroup
      if (group != null) {
        for (s <- group.asScala) {
          if (s != segment) {
            s.getActor.setHovered(false)
          }
        }
      }
    }

    override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
      val parent = getParent
      if (parent == null || !parent.isInstanceOf[TlGroup]) {
        return false
      }
      val tlGroup = parent.asInstanceOf[TlGroup]
      if (button == Input.Buttons.LEFT) {
        if (x < edgeWidth) {
          dragSide = DragSide.FRONT
        } else if (x > getWidth - edgeWidth) {
          dragSide = DragSide.BEHIND
        } else {
          dragSide = DragSide.MIDDLE
        }
        event.stop()
        val alreadySelected: Boolean = tlGroup.selectedSegments.contains(segment)
        if (!alreadySelected) {
          tlGroup.selectSegment(segment, false)
        }
        tl = tlGroup
        initDrag(x, y)
        true
      } else {
        getMenu.setContext(SegActor.this, parent.asInstanceOf[TlGroup].xToAbsoluteTime(getX + x))
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
    val s = getSegment.isSelected
    App.root.getShapeDrawer.rectangle(getX, getY, getWidth, getHeight, if (s) Color.WHITE else Colors.ACCENT, if (s) 6f else 2f)
  }

  private def drawSelectionOverlay(): Unit = {
    if (hovered) {
      App.root.getShapeDrawer.filledRectangle(getX, getY, getWidth, getHeight, Colors.SEGMENT_HOVER)
    }
  }

  private def setHovered(hovered: Boolean): Unit = {
    this.hovered = hovered
  }

  def getSegment: Segment = {
    segment
  }

  def getDragSide: DragSide = {
    dragSide
  }

  // 拖拽会话：init → drag → finish 由本 actor 驱动。碰撞由模型把 delta
  // 同向截断到最大可用偏移量处理，无需修正重试。actor 位置是模型的纯投影。

  /** 按下时调用：快照参与拖拽的成员并开始录制 undo。 */
  private[tlarea] def initDrag(diffToActorX: Float, diffToActorY: Float): Unit = {
    val r = segment.getRange
    dragOldStart = r.lo
    dragOldDuration = r.hi - dragOldStart
    firstX = diffToActorX
    firstY = diffToActorY
    tl.timeline.record()
    initDragMembers()
  }

  /** 拖拽中：每次鼠标移动都会调用，按 dragSide 分派到三种分支（含吸附）。 */
  private[tlarea] def dragTo(diffToActorX: Float, diffToActorY: Float): Unit = {
    if (tl == null || dragSide == DragSide.NONE) return

    tl.snapIndicatorTime = -1

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

        val duration: Long = segment.getRange.hi - segment.getRange.lo
        var target: Long = tl.xToAbsoluteTime(targetX)
        if (target < 0) target = 0
        target = snapMoveTarget(target, duration)

        val newTrack: Track = tl.timeline.getTrack(Math.max(0, tl.yToTrackIndex(targetY + tl.view.trackHeight / 2)))

        handleMiddleDrag(target, newTrack)
      case _ => ()
    }

    tl.dirty = true
  }

  // 吸附点由 Timeline.snapTime 获取，这里只做阈值换算、忽略集与指示线。

  private def snapThreshold(): Long = {
    Math.max(1, (SegActor.SNAP_THRESHOLD_PX / tl.getWidth * tl.view.durationTime).toLong)
  }

  private def snapDisabled(): Boolean = {
    App.shortcutManager.isActive(TlGroup.Actions.SNAP_IGNORE)
  }

  /** 裁切吸附：忽略 drag 成员与同轨道片段，返回吸附后的时间并设置指示线。 */
  private def snapResizeTime(rawTime: Long): Long = {
    if (snapDisabled()) return rawTime
    val ignore: util.Set[Segment] = new util.HashSet[Segment](dragMembers)
    for (s <- segment.getTrack.asScala) ignore.add(s)
    val snapped: Long = tl.timeline.snapTime(rawTime, snapThreshold(), ignore)
    if (snapped != rawTime) tl.snapIndicatorTime = snapped
    snapped
  }

  /** 整体移动吸附：起点与终点各求吸附点，取更近者。 */
  private def snapMoveTarget(target: Long, duration: Long): Long = {
    if (snapDisabled()) return target
    val ignore: util.Set[Segment] = new util.HashSet[Segment](dragMembers)
    val segEnd: Long = target + duration
    val snappedStart: Long = tl.timeline.snapTime(target, snapThreshold(), ignore)
    var snappedEnd: Long = tl.timeline.snapTime(segEnd, snapThreshold(), ignore) - duration
    if (snappedEnd < 0) snappedEnd = 0
    val startMoved: Boolean = snappedStart != target
    val endMoved: Boolean = snappedEnd != target
    if (startMoved && endMoved) {
      if (Math.abs(snappedStart - target) <= Math.abs(snappedEnd - target)) {
        tl.snapIndicatorTime = snappedStart
        return snappedStart
      }
      tl.snapIndicatorTime = snappedEnd + duration
      return snappedEnd
    } else if (startMoved) {
      tl.snapIndicatorTime = snappedStart
      return snappedStart
    } else if (endMoved) {
      tl.snapIndicatorTime = snappedEnd + duration
      return snappedEnd
    }
    target
  }

  /** 松手时调用：提交 undo 并清空会话。 */
  private[tlarea] def finishDrag(): Unit = {
    if (tl == null) return
    tl.dirty = true
    tl.snapIndicatorTime = -1
    tl.timeline.submit()
    dragMembers = null
    dragOrigStarts = null
    dragOrigDurations = null
    dragOrigTracks = null
  }

  /** 收集参与拖拽的成员并快照各自的起点/时长/轨道。 */
  private def initDragMembers(): Unit = {
    val selected = tl.selectedSegments
    if (selected.size() > 1 && selected.contains(segment)) {
      dragMembers = new util.ArrayList[Segment](selected.size())
      dragMembers.add(segment)
      for (s <- selected.asScala) {
        if (s != segment) dragMembers.add(s)
      }
    } else {
      dragMembers = new util.ArrayList[Segment](1)
      dragMembers.add(segment)
    }

    val n: Int = dragMembers.size()
    dragOrigStarts = new Array[Long](n)
    dragOrigDurations = new Array[Long](n)
    dragOrigTracks = new Array[Track](n)
    for (i <- 0 until n) {
      val sr = dragMembers.get(i).getRange
      dragOrigStarts(i) = sr.lo
      dragOrigDurations(i) = sr.hi - sr.lo
      dragOrigTracks(i) = dragMembers.get(i).getTrack
    }
  }

  // 整体平移。模型 moveTime/moveTrack 都按相对当前位置位移，因此这里的
  // delta 必须相对当前模型状态，避免同帧多次 mouse move 反复累加。
  // 模型内部会把 delta 同向截断到最大可用偏移量（撞上障碍即贴合）后应用，
  // 因此每次 mouse move 一次调用即可，无需按修正量重试。
  private def handleMiddleDrag(target: Long, newTrack: Track): Unit = {
    val members: util.List[Segment] = util.List.copyOf(dragMembers)

    val trackDelta: Int = newTrack.index - members.get(0).getTrack.index

    val minIdx: Int = members.stream().mapToInt((m: Segment) => m.getTrack.index).min().orElseThrow()
    if (minIdx + trackDelta < 0) return

    val currentStart0: Long = members.get(0).getRange.lo
    tl.timeline.moveTime(members, target - currentStart0)

    if (trackDelta != 0) {
      tl.timeline.moveTrack(members, trackDelta)
    }
  }

  private def handleFrontResize(newStart: Long): Unit = {
    val absDelta: Long = newStart - dragOldStart
    val n: Int = dragMembers.size()
    var i = 0
    while (i < n) {
      val ns: Long = dragOrigStarts(i) + absDelta
      if (ns >= dragOrigStarts(i) + dragOrigDurations(i) || ns < 0) return
      i += 1
    }

    val members: util.List[Segment] = util.List.copyOf(dragMembers)
    val currentStart0: Long = members.get(0).getRange.lo

    // 模型内部把 delta 同向截断到最大可用偏移量（自身长度/前邻/起点下界）后应用；
    // applied 为 0 等价于没动。
    tl.timeline.setStart(members, newStart - currentStart0)
  }

  private def handleBehindResize(newEnd: Long): Unit = {
    val oldEnd: Long = dragOldStart + dragOldDuration
    val absDelta: Long = newEnd - oldEnd
    val n: Int = dragMembers.size()
    var i = 0
    while (i < n) {
      val ne: Long = dragOrigStarts(i) + dragOrigDurations(i) + absDelta
      if (ne <= dragOrigStarts(i)) return
      i += 1
    }

    val members: util.List[Segment] = util.List.copyOf(dragMembers)
    val currentEnd0: Long = members.get(0).getRange.hi

    // 模型内部把 delta 同向截断到最大可用偏移量（自身长度/后邻/源长度上界）后应用；
    // applied 为 0 等价于没动。
    tl.timeline.setEnd(members, newEnd - currentEnd0)
  }

  private def setCursor(cursor: Cursor.SystemCursor): Unit = {
    if (Gdx.app.getType != Application.ApplicationType.Desktop) return
    Gdx.graphics.setSystemCursor(cursor)
  }

  private def getMenu: SegMenu = {
    getParent match {
      case g: TlGroup => g.segMenu
      case _ => null
    }
  }

  private[tlarea] def initMenu(): Unit = {
    if (!menuInitialized) {
      val menu: SegMenu = getMenu
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

object SegActor {
  private final val SNAP_THRESHOLD_PX: Float = 10f
}
