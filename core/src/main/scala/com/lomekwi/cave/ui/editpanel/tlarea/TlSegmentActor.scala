package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.{Application, Gdx, Input}
import com.badlogic.gdx.graphics.{Color, Cursor}
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.{Rectangle, Vector2}
import com.badlogic.gdx.scenes.scene2d.{Actor, InputEvent, InputListener}
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.lomekwi.cave.pipeline.{Content, Segment, Transition}
import com.lomekwi.cave.timeline.{Interval, SegmentGroup, Track}

import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.Colors


import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import java.util

/** 时间线上单个片段的可视化表示与交互入口。 */
abstract class TlSegmentActor(val segment: Segment[?]) extends Actor {
  private[tlarea] var tl: TimelineView = uninitialized
  var dragSide: DragSide = DragSide.NONE

  private[tlarea] var firstX: Float = Float.NaN
  private[tlarea] var firstY: Float = Float.NaN
  private var dragMembers: util.List[Segment[?]] = uninitialized

  private val scissors: Rectangle = new Rectangle()
  private val bounds: Rectangle = new Rectangle()
  private val viewCoords: Vector2 = new Vector2()
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
      // 用 stage 坐标换算到 TimelineView 本地，不依赖本 actor 的坐标系。
      // 拖转场时转场会在越过对边后消失，它的 actor 随即被移出舞台，parent 变成 null，
      // 此时 Gdx 算出的本地坐标会整体偏移一个 TimelineView 的位置
      viewCoords.set(event.getStageX, event.getStageY)
      if (tl != null) {
        tl.stageToLocalCoordinates(viewCoords)
      }
      dragTo(viewCoords.x, viewCoords.y)
    }

    override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
      finishDrag()
      dragSide = DragSide.NONE
      setCursor(Cursor.SystemCursor.Arrow)
    }
  })

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    if (range == null) return
    ScissorStack.calculateScissors(App.root.stage.getCamera, batch.getTransformMatrix, bounds, scissors)
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
    App.root.shapeDrawer.filledRectangle(getX, getY, getWidth, getHeight, Colors.ACCENT_LIGHT)
  }

  private def drawBorder(): Unit = {
    val s = isSelected
    App.root.shapeDrawer.rectangle(getX, getY, getWidth, getHeight, if (s) Color.WHITE else Colors.ACCENT, if (s) 6f else 2f)
  }

  private def drawSelectionOverlay(): Unit = {
    if (hovered) {
      App.root.shapeDrawer.filledRectangle(getX, getY, getWidth, getHeight, Colors.SRC_HOVER)
    }
  }

  private def setHovered(hovered: Boolean): Unit = {
    this.hovered = hovered
  }


  /** 选中态由视图持有，actor 只是查询者。 */
  private def isSelected: Boolean = {
    tl != null && tl.selectedSegments.contains(segment)
  }

  // 拖拽会话由本 actor 驱动，actor 位置是模型的纯投影。

  /** 按下时调用，快照参与拖拽的成员并开始录制 undo。 */
  private[tlarea] def initDrag(diffToActorX: Float, diffToActorY: Float): Unit = {
    firstX = diffToActorX
    firstY = diffToActorY
    tl.timeline.record()
    initDragMembers()
  }

  /**
   * 拖拽中，每次鼠标移动都会调用，按 dragSide 分派到三种分支（含吸附）。
   * 坐标是鼠标在 [[TimelineView]] 中的位置，不由本 actor 的坐标系换算。
   */
  private[tlarea] def dragTo(viewX: Float, viewY: Float): Unit = {
    if (tl == null || dragSide == DragSide.NONE) return

    tl.snapIndicatorTime = -1

    dragSide match {
      case DragSide.FRONT =>
        var target: Float = viewX
        target = Math.max(target, tl.absoluteTimeToX(0))
        handleFrontResize(snapResizeTime(Math.max(tl.xToAbsoluteTime(target), 0)))
      case DragSide.BEHIND =>
        val rawUpper: Long = Math.max(tl.xToAbsoluteTime(viewX), 0)
        handleBehindResize(snapResizeTime(rawUpper))
      case DragSide.MIDDLE =>
        // 锚点被连带摘除（如相邻内容删除时转场被删）后拖拽无从继续
        val r = range
        if (r == null) return
        // firstX/firstY 是按下时鼠标在片段内的偏移，viewX 减去它即片段起点
        val targetX: Float = viewX - firstX
        val targetY: Float = viewY - firstY

        val duration: Long = r.hi - r.lo
        var target: Long = tl.xToAbsoluteTime(targetX)
        if (target < 0) target = 0
        target = snapMoveTarget(target, duration)

        val newTrack: Track = tl.timeline.getTrackOrCreate(Math.max(0, tl.yToTrackIndex(targetY + tl.view.trackHeight / 2)))

        if (handleMiddleDrag(target, newTrack) != 0 && !dragMembers.isEmpty) {
          // 换轨会连带改变锚点的位置。以换轨后的状态重设按下点，
          // 否则下一帧会把这段位移当成新的一帧位移，内容整体平移过去
          val anchor = dragMembers.get(0)
          val anchorTrack = tl.timeline.findTrackOf(anchor)
          if (anchorTrack != null) {
            firstX = viewX - tl.absoluteTimeToX(anchorTrack.getRange(anchor).lo)
          }
        }
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
      // actor 的片段可能已被替换或摘除（如转场拖到消失），轨道按锚点取
      val t = if (dragMembers.isEmpty) null else tl.timeline.findTrackOf(dragMembers.get(0))
      if (t != null) {
        for (s <- t.asScala) {
          ignore.add(s)
        }
      }
      val snapped: Long = tl.timeline.snapTime(rawTime, snapThreshold(), ignore)
      if (snapped != rawTime) {
        tl.snapIndicatorTime = snapped
      }
      snapped
    }
  }

  /**
   * 整体移动吸附，起点与终点各求吸附点，取更近者。
   * 随拖动一起动的边都不参与吸附：成员自身，以及端点由成员端点派生的相邻转场。
   * 拖内容时邻居不动，它的边缘是静止的吸附点，照常参与。
   */
  private def snapMoveTarget(target: Long, duration: Long): Long = {
    if (snapDisabled()) {
      target
    } else {
      val ignore: util.Set[Segment[?]] = new util.HashSet[Segment[?]]()
      for (member <- dragMembers.asScala) {
        ignore.add(member)
        val memberTrack = tl.timeline.findTrackOf(member)
        if (memberTrack != null) {
          member match {
            case c: Content[?] =>
              val before = memberTrack.transitionBefore(c)
              if (before != null) ignore.add(before)
              val after = memberTrack.transitionAfter(c)
              if (after != null) ignore.add(after)
            case t: Transition[?] =>
              ignoreTransition(ignore, memberTrack, t)
            case _ =>
          }
        }
      }
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

  /**
   * 拖转场本体时两侧内容跟着平移（见 [[Track.shiftTransition]]），
   * 转场及其两侧内容的端点都在变，必须整体退出吸附。
   */
  private def ignoreTransition(ignore: util.Set[Segment[?]], track: Track, t: Transition[?]): Unit = {
    if (t != null) {
      ignore.add(t)
      val sides = track.transitionSides(t)
      if (sides != null) {
        ignore.add(sides._1)
        ignore.add(sides._2)
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
    }
  }

  /** 拖转场边缘就是拖某条内容边：左缘是右内容的起点，右缘是左内容的终点。 */
  private def resizeTarget(member: Segment[?]): Segment[?] = member match {
    case t: Transition[?] =>
      val memberTrack = tl.timeline.findTrackOf(t)
      if (memberTrack == null) {
        member
      } else {
        val sides = memberTrack.transitionSides(t)
        if (sides == null) member else if (dragSide == DragSide.FRONT) sides._2 else sides._1
      }
    case _ => member
  }

  /** 收集参与拖拽的成员并快照各自的起点与时长。 */
  private def initDragMembers(): Unit = {
    val selected = tl.selectedSegments
    val candidates = new util.ArrayList[Segment[?]]()
    if (selected.size() > 1 && selected.contains(segment)) {
      candidates.add(segment)
      for (s <- selected.asScala) {
        if (s != segment) candidates.add(s)
      }
    } else {
      candidates.add(segment)
    }

    // 边缘拖拽时一次换掉锚点，不在轨道上的成员不参与拖拽，否则按成员索引取区间会越界
    val replaceEdge = dragSide == DragSide.FRONT || dragSide == DragSide.BEHIND
    dragMembers = new util.ArrayList[Segment[?]](candidates.size())
    for (m <- candidates.asScala) {
      val member = if (replaceEdge) resizeTarget(m) else m
      if (tl.timeline.findTrackOf(member) != null && !dragMembers.contains(member)) {
        dragMembers.add(member)
      }
    }
  }

  /** 整体移动一步；返回实际应用的轨道偏移，0 表示未换轨。 */
  private def handleMiddleDrag(target: Long, newTrack: Track): Int = {
    // 成员里的转场可能被相邻内容的删除连带摘掉，只保留仍在轨道上的成员参与移动
    val members: util.List[Segment[?]] = new util.ArrayList[Segment[?]](dragMembers.size())
    for (m <- dragMembers.asScala) {
      if (tl.timeline.findTrackOf(m) != null) members.add(m)
    }
    if (members.isEmpty) return 0

    val firstTrack: Track = tl.timeline.findTrackOf(members.get(0))
    val trackDelta: Int = newTrack.index - firstTrack.index

    var appliedTracks: Int = 0
    val minIdx: Int = members.asScala.iterator.map((m: Segment[?]) => tl.timeline.findTrackOf(m).index).min
    if (minIdx + trackDelta >= 0) {
      val currentStart0: Long = firstTrack.getRange(members.get(0)).lo
      tl.timeline.moveTime(members, target - currentStart0)

      if (trackDelta != 0) {
        appliedTracks = tl.timeline.moveTrack(members, trackDelta)
      }
    }
    appliedTracks
  }

  private def handleFrontResize(newStart: Long): Unit = {
    if (dragMembers.isEmpty) return
    val members: util.List[Segment[?]] = util.List.copyOf(dragMembers)
    val first = members.get(0)
    val firstTrack = tl.timeline.findTrackOf(first)
    if (firstTrack == null) return
    // 越界由 setStart 自己的夹取处理，这里不再用按下时的快照预判，否则拖过一次头就永久卡住
    val base = firstTrack.getRange(first).lo
    tl.timeline.setStart(members, newStart - base)
  }

  private def handleBehindResize(newEnd: Long): Unit = {
    if (dragMembers.isEmpty) return
    val members: util.List[Segment[?]] = util.List.copyOf(dragMembers)
    val first = members.get(0)
    val firstTrack = tl.timeline.findTrackOf(first)
    if (firstTrack == null) return
    val r = firstTrack.getRange(first)
    tl.timeline.setEnd(members, newEnd - r.hi)
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
