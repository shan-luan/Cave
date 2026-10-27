package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.{Color, Cursor}
import com.badlogic.gdx.math.{Rectangle, Vector2}
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.scenes.scene2d.{Actor, InputEvent, InputListener}
import com.badlogic.gdx.{Application, Gdx, Input}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.project.{Id, ProjectSession}
import com.lomekwi.cave.timeline.*
import com.lomekwi.cave.ui.Colors

import scala.collection.mutable
import scala.compiletime.uninitialized

/** 时间线上单个片段的可视化表示与交互入口。 */
abstract class TlSegmentActor(initial: Segment) extends Actor {
  final val id: Id = initial.id
  private[tlarea] var segment: Segment = initial
  private[tlarea] var track: Track = uninitialized
  private[tlarea] var tl: TimelineView = uninitialized
  var dragSide: DragSide = DragSide.NONE

  private[tlarea] var firstX: Float = Float.NaN
  private[tlarea] var firstY: Float = Float.NaN
  private var dragMembers: mutable.ArrayBuffer[Id] = uninitialized
  private var dragRecording: ProjectSession.Recording = uninitialized

  private val scissors: Rectangle = new Rectangle()
  private val bounds: Rectangle = new Rectangle()
  private val viewCoords: Vector2 = new Vector2()
  private var hovered: Boolean = false
  private var menuInitialized: Boolean = false

  /** 本片段占用的时间区间；未在时间线上时为空。 */
  private[tlarea] def interval: Interval = {
    if (track == null) null else track.interval(id)
  }

  /** 片段的 0 秒在时间轴中的位置；未在时间线上时为 0。 */
  private[tlarea] def origin: Long = {
    if (track == null) 0L else track.getOrigin(id)
  }

  /** 本片段所属的组；不属于任何组时为空。 */
  private[tlarea] def group: SegmentGroup = {
    if (tl == null) null else tl.timeline.getGroup(id).orNull
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
        for (memberId <- g) {
          if (memberId != id) {
            tl.actorById.get(memberId).foreach(_.setHovered(true))
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
        for (memberId <- g) {
          if (memberId != id) {
            tl.actorById.get(memberId).foreach(_.setHovered(false))
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
        val alreadySelected: Boolean = timelineView.selectedSegments.contains(id)
        if (!alreadySelected) {
          timelineView.selectSegment(id, false)
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
    if (interval == null) return
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
    tl != null && tl.selectedSegments.contains(id)
  }

  // 拖拽会话由本 actor 驱动，actor 位置是模型的纯投影。

  /** 按下时调用，快照参与拖拽的成员并开始录制 undo。 */
  private[tlarea] def initDrag(diffToActorX: Float, diffToActorY: Float): Unit = {
    firstX = diffToActorX
    firstY = diffToActorY
    dragRecording = tl.context.record()
    initDragMembers()
    tl.draggingActor = this
  }

  /**
   * 拖拽中每帧调用，按 [[dragSide]] 分派到三种分支（含吸附）。
   * 驱动来自鼠标移动事件与 [[TimelineView.act]] 两处，本方法幂等，重复调用无副作用。
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
        val r = interval
        if (r == null) return
        // firstX/firstY 是按下时鼠标在片段内的偏移，viewX 减去它即片段起点
        val targetX: Float = viewX - firstX
        val targetY: Float = viewY - firstY

        val duration: Long = r.len
        var target: Long = tl.xToAbsoluteTime(targetX)
        if (target < 0) target = 0
        target = snapMoveTarget(target, duration)

        val newTrack: Track = tl.timeline.getTrackOrCreate(Math.max(0, tl.yToTrackIndex(targetY + tl.view.trackHeight / 2)))

        if (handleMiddleDrag(target, newTrack) != 0 && dragMembers.nonEmpty) {
          // 换轨会连带改变锚点的位置。以换轨后的状态重设按下点，
          // 否则下一帧会把这段位移当成新的一帧位移，内容整体平移过去
          val anchor = dragMembers.head
          tl.timeline.findTrackOf(anchor).foreach { anchorTrack =>
            firstX = viewX - tl.absoluteTimeToX(anchorTrack.interval(anchor).lo)
          }
        }
      case _ => ()
    }

    tl.dirty = true
  }

  // 吸附点由 [[com.lomekwi.cave.timeline.Timeline.snapTime]] 获取，这里只做阈值换算、忽略集与指示线。

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
      val ignore: mutable.HashSet[Id] = mutable.HashSet.from(dragMembers)
      // actor 的片段可能已被替换或摘除（如转场拖到消失），轨道按锚点取
      if (dragMembers.nonEmpty) {
        tl.timeline.findTrackOf(dragMembers.head).foreach { t =>
          ignore ++= t.iterator
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
   * 随拖动一起动的边都不参与吸附，包括成员自身以及端点由成员端点派生的相邻转场。
   * 拖内容时邻居不动，它的边缘是静止的吸附点，照常参与。
   */
  private def snapMoveTarget(target: Long, duration: Long): Long = {
    if (snapDisabled()) {
      target
    } else {
      val ignore: mutable.HashSet[Id] = mutable.HashSet.empty
      for (memberId <- dragMembers) {
        ignore += memberId
        tl.timeline.findTrackOf(memberId).foreach { memberTrack =>
          memberTrack.segment(memberId) match {
            case c: Content =>
              memberTrack.transitionBefore(c).foreach(t => ignore += t.id)
              memberTrack.transitionAfter(c).foreach(t => ignore += t.id)
            case t: Transition =>
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

  /** 把转场及其两侧内容整体加入忽略集。 */
  private def ignoreTransition(ignore: mutable.Set[Id], track: Track, t: Transition): Unit = {
    // 拖转场本体时两侧内容跟着平移（见 [[Track.shiftTransition]]），转场及其两侧内容的端点都在变，必须整体退出吸附
    if (t != null) {
      ignore += t.id
      track.transitionSides(t).foreach { case (left, right) =>
        ignore += left.id
        ignore += right.id
      }
    }
  }

  /** 松手时调用，提交 undo 并清空会话。 */
  private[tlarea] def finishDrag(): Unit = {
    if (tl != null) {
      tl.draggingActor = null
      tl.dirty = true
      tl.snapIndicatorTime = -1
      if (dragRecording != null) {
        dragRecording.close()
        dragRecording = null
      }
      dragMembers = null
    }
  }

  /** 拖转场边缘就是拖某条内容边，左缘是右内容的起点，右缘是左内容的终点。 */
  private def resizeTarget(memberId: Id): Id = memberId match {
    case _ =>
      tl.timeline.findTrackOf(memberId) match {
        case None => memberId
        case Some(memberTrack) =>
          memberTrack.segment(memberId) match {
            case t: Transition =>
              memberTrack.transitionSides(t) match {
                case Some((left, right)) => if (dragSide == DragSide.FRONT) right.id else left.id
                case None => memberId
              }
            case _ => memberId
          }
      }
  }

  /** 收集参与拖拽的成员并快照各自的起点与时长。 */
  private def initDragMembers(): Unit = {
    val selected = tl.selectedSegments
    val candidates = mutable.ArrayBuffer.empty[Id]
    if (selected.size > 1 && selected.contains(id)) {
      candidates += id
      for (memberId <- selected) {
        if (memberId != id) candidates += memberId
      }
    } else {
      candidates += id
    }

    // 边缘拖拽时一次换掉锚点，不在轨道上的成员不参与拖拽，否则按成员索引取区间会越界
    val replaceEdge = dragSide == DragSide.FRONT || dragSide == DragSide.BEHIND
    dragMembers = mutable.ArrayBuffer.empty
    for (m <- candidates) {
      val member = if (replaceEdge) resizeTarget(m) else m
      if (tl.timeline.findTrackOf(member).isDefined && !dragMembers.contains(member)) {
        dragMembers += member
      }
    }
  }

  /** 整体移动一步；返回实际应用的轨道偏移，0 表示未换轨。 */
  private def handleMiddleDrag(target: Long, newTrack: Track): Int = {
    // 成员里的转场可能被相邻内容的删除连带摘掉，只保留仍在轨道上的成员参与移动
    val placed: mutable.ArrayBuffer[(Id, Track)] = mutable.ArrayBuffer.empty
    for (memberId <- dragMembers) {
      tl.timeline.findTrackOf(memberId).foreach(t => placed += ((memberId, t)))
    }
    if (placed.isEmpty) return 0
    val members: mutable.ArrayBuffer[Id] = placed.map(_._1)

    val firstTrack: Track = placed.head._2
    val trackDelta: Int = newTrack.index - firstTrack.index

    var appliedTracks: Int = 0
    val minIdx: Int = placed.iterator.map(_._2.index).min
    if (minIdx + trackDelta >= 0) {
      val currentStart0: Long = firstTrack.interval(members.head).lo
      tl.context.moveTime(members, target - currentStart0)

      if (trackDelta != 0) {
        appliedTracks = tl.context.moveTrack(members, trackDelta)
      }
    }
    appliedTracks
  }

  private def handleFrontResize(newStart: Long): Unit = {
    if (dragMembers.isEmpty) return
    val members: mutable.ArrayBuffer[Id] = mutable.ArrayBuffer.from(dragMembers)
    val first = members.head
    tl.timeline.findTrackOf(first).foreach { firstTrack =>
      // 越界由 setStart 自己的夹取处理，这里不再用按下时的快照预判，否则拖过一次头就永久卡住
      val base = firstTrack.interval(first).lo
      tl.context.setStart(members, newStart - base)
    }
  }

  private def handleBehindResize(newEnd: Long): Unit = {
    if (dragMembers.isEmpty) return
    val members: mutable.ArrayBuffer[Id] = mutable.ArrayBuffer.from(dragMembers)
    val first = members.head
    tl.timeline.findTrackOf(first).foreach { firstTrack =>
      val r = firstTrack.interval(first)
      tl.context.setEnd(members, newEnd - r.hi)
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
  private val SNAP_THRESHOLD_PX: Float = 10f
}
