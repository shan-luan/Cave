package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.{Color, Cursor}
import com.badlogic.gdx.math.{Rectangle, Vector2}
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.scenes.scene2d.{Actor, InputEvent, InputListener}
import com.badlogic.gdx.{Application, Gdx, Input}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.project.Id
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

  private val scissors: Rectangle = new Rectangle()
  private val bounds: Rectangle = new Rectangle()
  private val viewCoords: Vector2 = new Vector2()
  private var hovered: Boolean = false
  private var menuInitialized: Boolean = false

  /** 本片段占用的时间区间。 */
  private[tlarea] def interval: Interval = {
    if (track == null) null else track.interval(id)
  }

  /** 片段的 0 秒在时间轴中的位置。 */
  private[tlarea] def origin: Instant = {
    if (track == null) Instant.Zero else track.getOrigin(id)
  }

  /** 本片段所属的组。 */
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

  private def isSelected: Boolean = {
    tl != null && tl.selectedSegments.contains(id)
  }

  /** 开始拖拽会话。 */
  private[tlarea] def initDrag(diffToActorX: Float, diffToActorY: Float): Unit = {
    firstX = diffToActorX
    firstY = diffToActorY
    tl.session.snapshot()
    initDragMembers()
    tl.draggingActor = this
  }

  /** 拖拽中每帧更新片段。 */
  private[tlarea] def dragTo(viewX: Float, viewY: Float): Unit = {
    if (tl == null || dragSide == DragSide.NONE) return

    tl.snapIndicatorTime = None

    dragSide match {
      case DragSide.FRONT =>
        var target: Float = viewX
        target = Math.max(target, tl.absoluteTimeToX(Instant.Zero))
        handleFrontResize(snapResizeTime(tl.xToAbsoluteTime(target).max(Instant.Zero)))
      case DragSide.BEHIND =>
        val rawUpper: Instant = tl.xToAbsoluteTime(viewX).max(Instant.Zero)
        handleBehindResize(snapResizeTime(rawUpper))
      case DragSide.MIDDLE =>
        val r = interval
        if (r == null) return
        val targetX: Float = viewX - firstX
        val targetY: Float = viewY - firstY

        val duration: Timespan = r.len
        var target: Instant = tl.xToAbsoluteTime(targetX).max(Instant.Zero)
        target = snapMoveTarget(target, duration)

        val newTrack: Track = tl.timeline.getTrackOrCreate(Math.max(0, tl.yToTrackIndex(targetY + tl.view.trackHeight / 2)))

        if (handleMiddleDrag(target, newTrack) != 0 && dragMembers.nonEmpty) {
          val anchor = dragMembers.head
          tl.timeline.findTrackOf(anchor).foreach { anchorTrack =>
            firstX = viewX - tl.absoluteTimeToX(anchorTrack.interval(anchor).lo)
          }
        }
      case _ => ()
    }

    tl.dirty = true
  }

  private def snapThreshold(): Timespan = {
    Timespan(1).max(tl.view.durationTime * (TlSegmentActor.SNAP_THRESHOLD_PX.toDouble / tl.getWidth))
  }

  private def snapDisabled(): Boolean = {
    App.shortcutManager.isActive(TimelineView.Actions.SNAP_IGNORE)
  }

  /** 裁切吸附，返回吸附后的时间。 */
  private def snapResizeTime(rawTime: Instant): Instant = {
    if (snapDisabled()) {
      rawTime
    } else {
      val ignore: mutable.HashSet[Id] = mutable.HashSet.from(dragMembers)
      if (dragMembers.nonEmpty) {
        tl.timeline.findTrackOf(dragMembers.head).foreach { t =>
          ignore ++= t.iterator
        }
      }
      val snapped: Instant = tl.timeline.snapTime(rawTime, snapThreshold(), ignore)
      if (snapped != rawTime) {
        tl.snapIndicatorTime = Some(snapped)
      }
      snapped
    }
  }

  /** 整体移动吸附，起点与终点各求吸附点并取更近者。 */
  private def snapMoveTarget(target: Instant, duration: Timespan): Instant = {
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
      val srcEnd: Instant = target + duration
      val snappedStart: Instant = tl.timeline.snapTime(target, snapThreshold(), ignore)
      var snappedEnd: Instant = tl.timeline.snapTime(srcEnd, snapThreshold(), ignore) - duration
      if (snappedEnd < Instant.Zero) snappedEnd = Instant.Zero
      val startMoved: Boolean = snappedStart != target
      val endMoved: Boolean = snappedEnd != target
      if (startMoved && endMoved) {
        if ((snappedStart - target).abs <= (snappedEnd - target).abs) {
          tl.snapIndicatorTime = Some(snappedStart)
          snappedStart
        } else {
          tl.snapIndicatorTime = Some(snappedEnd + duration)
          snappedEnd
        }
      } else if (startMoved) {
        tl.snapIndicatorTime = Some(snappedStart)
        snappedStart
      } else if (endMoved) {
        tl.snapIndicatorTime = Some(snappedEnd + duration)
        snappedEnd
      } else {
        target
      }
    }
  }

  /** 把转场及其两侧内容整体加入忽略集。 */
  private def ignoreTransition(ignore: mutable.Set[Id], track: Track, t: Transition): Unit = {
    if (t != null) {
      ignore += t.id
      track.transitionSides(t).foreach { case (left, right) =>
        ignore += left.id
        ignore += right.id
      }
    }
  }

  /** 结束拖拽会话。 */
  private[tlarea] def finishDrag(): Unit = {
    if (tl != null) {
      tl.draggingActor = null
      tl.dirty = true
      tl.snapIndicatorTime = None
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

  /** 收集参与拖拽的成员。 */
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

    val replaceEdge = dragSide == DragSide.FRONT || dragSide == DragSide.BEHIND
    dragMembers = mutable.ArrayBuffer.empty
    for (m <- candidates) {
      val member = if (replaceEdge) resizeTarget(m) else m
      if (tl.timeline.findTrackOf(member).isDefined && !dragMembers.contains(member)) {
        dragMembers += member
      }
    }
  }

  /** 整体移动一步，返回实际应用的轨道偏移。 */
  private def handleMiddleDrag(target: Instant, newTrack: Track): Int = {
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
      val currentStart0: Instant = firstTrack.interval(members.head).lo
      val (moved, _) = tl.timeline.moveTime(members, target - currentStart0)(using tl.session.idSource)
      tl.session.applyProject(_.withTimeline(moved))

      if (trackDelta != 0) {
        val (movedTrack, applied) = tl.timeline.moveTrack(members, trackDelta)(using tl.session.idSource)
        tl.session.applyProject(_.withTimeline(movedTrack))
        appliedTracks = applied
      }
    }
    appliedTracks
  }

  private def handleFrontResize(newStart: Instant): Unit = {
    if (dragMembers.isEmpty) return
    val members: mutable.ArrayBuffer[Id] = mutable.ArrayBuffer.from(dragMembers)
    val first = members.head
    tl.timeline.findTrackOf(first).foreach { firstTrack =>
      val base = firstTrack.interval(first).lo
      val (next, _) = tl.timeline.setStart(members, newStart - base)(using tl.session.idSource)
      tl.session.applyProject(_.withTimeline(next))
    }
  }

  private def handleBehindResize(newEnd: Instant): Unit = {
    if (dragMembers.isEmpty) return
    val members: mutable.ArrayBuffer[Id] = mutable.ArrayBuffer.from(dragMembers)
    val first = members.head
    tl.timeline.findTrackOf(first).foreach { firstTrack =>
      val r = firstTrack.interval(first)
      val (next, _) = tl.timeline.setEnd(members, newEnd - r.hi)(using tl.session.idSource)
      tl.session.applyProject(_.withTimeline(next))
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
