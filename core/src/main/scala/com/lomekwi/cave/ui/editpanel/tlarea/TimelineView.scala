package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Input.Keys.*
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.{Color, Cursor}
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.{Group, InputEvent}
import com.badlogic.gdx.{Gdx, Input}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.app.copy.PasteTemplate
import com.lomekwi.cave.app.selection.{SegmentSet, SegmentSetSelectedEvent}
import com.lomekwi.cave.app.shortcut.ShortcutAction
import com.lomekwi.cave.playback.Playhead
import com.lomekwi.cave.project.{Id, ProjectSession}
import com.lomekwi.cave.timeline.*
import com.lomekwi.cave.timeline.Timespan.*
import com.lomekwi.cave.ui.{Colors, Focusable}
import com.lomekwi.cave.util.niceScale
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import space.earlygrey.shapedrawer.ShapeDrawer

import scala.collection.mutable
import scala.compiletime.uninitialized

class TimelineView(private[tlarea] final val session: ProjectSession) extends Group with Focusable {

  private final val renderer: TimelineRenderer = new TimelineRenderer()
  final val srcMenu: TlSegmentMenu = new TlSegmentMenu(this)
  final val viewMenu: TlMenu = new TlMenu(this)

  private[tlarea] def timeline: Timeline = session.timeline
  private[tlarea] def playhead: Playhead = session.playhead

  private[tlarea] final val view: TimelineView.ViewState = new TimelineView.ViewState()

  /** 拖拽吸附时的吸附时间点。 */
  private[tlarea] var snapIndicatorTime: Option[Instant] = None

  private[tlarea] var dirty: Boolean = true
  private[tlarea] var selectedSegments: SegmentSet = uninitialized
  private[tlarea] val actorById: mutable.HashMap[Id, TlSegmentActor] = mutable.HashMap.empty

  private final val pointer: Vector2 = new Vector2()

  private final val inputListener: TlInputListener = new TlInputListener(this)
  private final val captureListener: TlCaptureListener = new TlCaptureListener(this)

  /** 当前有拖拽会话的片段。 */
  private[tlarea] var draggingActor: TlSegmentActor = uninitialized

  /** [[TimelineView.Actions.SEEK]] 快捷键按住时的刷动会话标志。 */
  private var seekScrubbing: Boolean = false

  private[tlarea] var marqueeActive: Boolean = false
  private[tlarea] var marqueeStartX: Float = 0
  private[tlarea] var marqueeStartY: Float = 0
  private[tlarea] var marqueeEndX: Float = 0
  private[tlarea] var marqueeEndY: Float = 0

  selectedSegments = new SegmentSet(session)

  session.bus.register(this)

  this.view.startTime = Instant.Zero
  this.view.durationTime = 30.seconds.max(session.timeline.getLength - Instant.Zero)
  this.view.trackHeight = 80

  addDefaultListeners()

  private def addDefaultListeners(): Unit = {
    addListener(inputListener)
    addCaptureListener(captureListener)
    App.root.dragAndDrop.addTarget(new TlDropTarget(this))
    addListener(new DragListener {
      setButton(Input.Buttons.MIDDLE)

      override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
        if (super.touchDown(event, x, y, pointer, button)) {
          Gdx.graphics.setSystemCursor(Cursor.SystemCursor.AllResize)
          true
        } else {
          false
        }
      }

      override def drag(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
        view.scrollHorizontal(-getDeltaX, getWidth)
        view.trackYShift = Math.max(0, view.trackYShift + getDeltaY)
        dirty = true
        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.AllResize)
      }

      override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
        super.touchUp(event, x, y, pointer, button)
        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow)
      }
    })
  }

  override def act(delta: Float): Unit = {
    super.act(delta)

    if (getStage != null) {
      pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)
      getStage.screenToStageCoordinates(pointer)
      stageToLocalCoordinates(pointer)

      var acted = false

      if (!App.root.isTextInputFocused && (getStage.getKeyboardFocus eq this)) {
        if (App.shortcutManager.isActive(TimelineView.Actions.SCROLL_RIGHT)) {
          view.startTime += view.durationTime * (TimelineView.KEY_HORIZONTAL_SPEED * delta.toDouble / getWidth)
          acted = true
        }
        if (App.shortcutManager.isActive(TimelineView.Actions.SCROLL_LEFT)) {
          view.startTime = Instant.Zero.max(view.startTime - view.durationTime * (TimelineView.KEY_HORIZONTAL_SPEED * delta.toDouble / getWidth))
          acted = true
        }
        if (App.shortcutManager.isActive(TimelineView.Actions.SCROLL_DOWN)) {
          view.trackYShift = Math.max(0, view.trackYShift + TimelineView.KEY_VERTICAL_SPEED * delta)
          acted = true
        }
        if (App.shortcutManager.isActive(TimelineView.Actions.SCROLL_UP)) {
          view.trackYShift = Math.max(0, view.trackYShift - TimelineView.KEY_VERTICAL_SPEED * delta)
          acted = true
        }

        if (App.shortcutManager.isActive(TimelineView.Actions.SEEK)) {
          seekPlayheadAtX(pointer.x)
          acted = true
        }
      }

      if (acted) dirty = true

      if (App.shortcutManager.isActive(TimelineView.Actions.SEEK)) {
        if (!seekScrubbing) {
          playhead.beginScrub()
          seekScrubbing = true
        }
      } else if (seekScrubbing) {
        playhead.endScrub()
        seekScrubbing = false
      }

      if (draggingActor != null || inputListener.scrubbing || marqueeActive) {
        pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)
        getStage.screenToStageCoordinates(pointer)
        stageToLocalCoordinates(pointer)

        if (draggingActor != null) draggingActor.dragTo(pointer.x, pointer.y)
        if (inputListener.scrubbing) seekPlayheadAtX(pointer.x)
        if (marqueeActive) {
          marqueeEndX = pointer.x
          marqueeEndY = pointer.y
        }
      }
    }

    if (dirty) {
      clearChildren(false)

      val visibleInterval = view.visibleInterval()
      val nextActors = mutable.HashMap.empty[Id, TlSegmentActor]
      for (i <- timeline.getTracks.indices.reverse) {
        val track = timeline.getTracks(i)

        val entries = track.getIntersecting(visibleInterval).sortBy {
          case _: Transition => 1
          case _ => 0
        }
        for (s <- entries) {
          val id = s.id
          val actor = actorById.getOrElseUpdate(id, s.getTlSegmentActor)
          nextActors(id) = actor
          actor.tl = this
          actor.track = track
          actor.segment = track.segment(id)
          val r = track.interval(id)
          actor.setPosition(
            absoluteTimeToX(r.lo),
            getHeight + view.trackYShift - (i + 1) * view.trackHeight
          )
          actor.setSize(
            absoluteTimeToX(r.hi) - absoluteTimeToX(r.lo),
            view.trackHeight
          )
          addActor(actor)
          actor.initMenu()
        }
      }
      actorById.clear()
      actorById ++= nextActors

      dirty = false
    }
  }

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    renderer.drawBackground()
    renderer.drawTrackBands()
    renderer.drawTicks()
    batch.setColor(Color.WHITE)
    super.draw(batch, parentAlpha)
    renderer.drawPlayhead()
    snapIndicatorTime.foreach { time =>
      val x = absoluteTimeToX(time)
      renderer.shapeDrawer.line(x, 0, x, getHeight, Colors.SNAP_GUIDE, 2)
    }
    if (marqueeActive) {
      val x = Math.min(marqueeStartX, marqueeEndX)
      val y = Math.min(marqueeStartY, marqueeEndY)
      val w = Math.abs(marqueeEndX - marqueeStartX)
      val h = Math.abs(marqueeEndY - marqueeStartY)
      renderer.shapeDrawer.filledRectangle(x, y, w, h, new Color(1, 1, 1, 0.3f))
      renderer.shapeDrawer.rectangle(x, y, w, h, Color.WHITE)
    }
  }

  def dispose(): Unit = {
    session.bus.unregister(this)
  }

  private[tlarea] def absoluteTimeToX(time: Instant): Float = {
    view.timeToX(time, getWidth)
  }

  private[tlarea] def xToAbsoluteTime(x: Float): Instant = {
    view.xToTime(x, getWidth)
  }

  private[tlarea] def seekPlayheadAtX(x: Float): Unit = {
    playhead.seek(xToAbsoluteTime(x).max(Instant.Zero))
  }

  /** 单选/追加选择，选中的是组则整组一起切换。 */
  def selectSegment(id: Id, addToSelection: Boolean): Unit = {
    timeline.getGroup(id) match {
      case Some(group) =>
        if (addToSelection) {
          val anySelected = group.exists(memberId => selectedSegments.contains(memberId))
          for (memberId <- group) {
            if (anySelected) selectedSegments.remove(memberId) else selectedSegments.add(memberId)
          }
        } else {
          selectedSegments.clear()
          for (memberId <- group) {
            selectedSegments.add(memberId)
          }
        }
      case None =>
        if (!addToSelection) {
          selectedSegments.clear()
          selectedSegments.add(id)
        } else if (selectedSegments.contains(id)) {
          selectedSegments.remove(id)
        } else {
          selectedSegments.add(id)
        }
    }
    publishSelection()
  }

  /** 整体替换选中集。 */
  private[tlarea] def selectSegments(ids: Iterable[Id]): Unit = {
    selectedSegments.clear()
    for (id <- ids) {
      selectedSegments.add(id)
    }
    publishSelection()
  }

  def clearSelection(): Unit = {
    selectedSegments.clear()
    publishSelection()
  }

  private def publishSelection(): Unit = {
    val e = SegmentSetSelectedEvent(selectedSegments, selectedSegments.size)
    session.bus.post(e)
    App.appEventBus.post(e)
  }

  private[tlarea] def removeSegment(segmentActor: TlSegmentActor): Unit = {
    removeActor(segmentActor)
    session.snapshot()
    session.applyProject(p => p.withTimeline(p.timeline.remove(segmentActor.id)(using session.idSource)))
    dirty = true
  }

  /** 右键菜单“分割”入口。 */
  private[tlarea] def split(segmentActor: TlSegmentActor, time: Instant): Unit = {
    splitSegment(segmentActor.id, time)
    dirty = true
  }

  /** 快捷键粘贴入口，按当前鼠标位置定位落点，粘贴后选中新片段并刷新剪贴板。 */
  private[tlarea] def pasteAtCursor(): Unit = {
    val stage = getStage
    if (stage == null) return
    App.copyManager.clipboard match {
      case template: PasteTemplate =>
        val local = stageToLocalCoordinates(
          stage.screenToStageCoordinates(pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)))
        val baseTime = xToAbsoluteTime(local.x).max(Instant.Zero)
        val baseTrack = Math.max(yToTrackIndex(local.y), 0)
        session.snapshot()
        val pasted = template.paste(session, baseTrack, baseTime)
        if (pasted.nonEmpty) {
          selectSegments(pasted)
        }
        App.copyManager.refreshClipboard()
        markTimelineDirty()
      case _ =>
    }
  }

  /** 快捷键分割入口，按当前鼠标位置定位分割点。 */
  private[tlarea] def splitAtCursor(): Unit = {
    val stage = getStage
    if (stage != null) {
      val local = stageToLocalCoordinates(
        stage.screenToStageCoordinates(pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)))
      val track = timeline.getTrackOrCreate(yToTrackIndex(local.y))
      track.get(xToAbsoluteTime(local.x)) match {
        case s: Segment =>
          splitSegment(s.id, xToAbsoluteTime(local.x))
          dirty = true
        case _: Gap =>
      }
    }
  }

  private def splitSegment(id: Id, time: Instant): Unit = {
    if (timeline.findTrackOf(id).isEmpty) return
    val group = timeline.getGroup(id)
    val members: Seq[Id] = group.map(_.toList).getOrElse(List(id))
    val beforeSegments: mutable.ArrayBuffer[Id] = mutable.ArrayBuffer.empty
    val afterSegments: mutable.ArrayBuffer[Id] = mutable.ArrayBuffer.empty
    var splitAny = false
    session.snapshot()
    for (memberId <- members) {
      session.timeline.findTrackOf(memberId).foreach { memberTrack =>
        val interval = memberTrack.interval(memberId)
        val start: Instant = interval.lo
        val end: Instant = interval.hi
        if (time > start && time < end) {
          session.applyProject(p => p.withTimeline(p.timeline.split(memberTrack, time)(using session.idSource)))
          beforeSegments += memberId
          session.timeline.getTrackOrCreate(memberTrack.index).get(time) match {
            case s: Segment => afterSegments += s.id
            case _: Gap =>
          }
          splitAny = true
        } else if (end <= time) {
          beforeSegments += memberId
        } else {
          afterSegments += memberId
        }
      }
    }
    if (splitAny) {
      session.snapshot()
      for (g <- group) {
        session.applyProject(_.withTimeline(session.timeline.dropGroup(g)))
        if (beforeSegments.size >= 2) regroup(beforeSegments)
        if (afterSegments.size >= 2) regroup(afterSegments)
      }
    }
  }

  private def deleteAtCursor(): Unit = {
    val stage = getStage
    if (stage != null) {
      val local = stageToLocalCoordinates(
        stage.screenToStageCoordinates(pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)))
      val track = timeline.getTrackOrCreate(yToTrackIndex(local.y))
      track.get(xToAbsoluteTime(local.x)) match {
        case s: Segment =>
          session.snapshot()
          session.applyProject(p => p.withTimeline(p.timeline.remove(s.id)(using session.idSource)))
          dirty = true
        case _: Gap =>
      }
    }
  }

  private[tlarea] def deleteSelected(): Unit = {
    if (selectedSegments.isEmpty) {
      deleteAtCursor()
    } else {
      val ids: Seq[Id] = selectedSegments.toSeq
      clearSelection()
      session.snapshot()
      session.applyProject(p => p.withTimeline(p.timeline.remove(ids)(using session.idSource)))
      dirty = true
    }
  }

  /** 选中的片段里但凡有已分组的就先解散，否则把它们合成一组。 */
  private[tlarea] def groupSelectedSegments(): Unit = {
    if (selectedSegments.size < 2) return

    val anyInGroup = selectedSegments.exists(id => session.timeline.getGroup(id).isDefined)

    if (anyInGroup) {
      session.snapshot()
      session.applyProject { p =>
        var t = p.timeline
        for (id <- selectedSegments) {
          t.getGroup(id).foreach { group => t = t.replaceGroup(group, group.remove(id)) }
        }
        p.withTimeline(Timeline(t.tracks, t.groups.filter(_.size >= 2)))
      }
    } else {
      session.snapshot()
      val (t, group) = session.timeline.newGroup()
      session.applyProject(_.withTimeline(t.replaceGroup(group, group.addAll(selectedSegments))))
    }
    dirty = true
  }
  /** 把一组成员合成新组。 */
  private def regroup(members: Iterable[Id]): SegmentGroup = {
    val (t, group) = session.timeline.newGroup()
    val filled = group.addAll(members)
    session.applyProject(_.withTimeline(t.replaceGroup(group, filled)))
    filled
  }

  private[tlarea] def yToTrackIndex(y: Float): Int = {
    val top = getHeight + view.trackYShift
    val distance = top - y
    Math.max(0f, Math.floor(distance / view.trackHeight)).toInt
  }

  private[tlarea] def trackIndexToTopY(index: Int): Float = {
    getHeight + view.trackYShift - index * view.trackHeight
  }

  def markTimelineDirty(): Unit = {
    dirty = true
  }

  override def sizeChanged(): Unit = {
    dirty = true
  }

  private[tlarea] class TimelineRenderer {
    private[tlarea] final val shapeDrawer: ShapeDrawer = App.root.shapeDrawer

    private[tlarea] def drawBackground(): Unit = {
      shapeDrawer.filledRectangle(0, 0, getWidth, getHeight, Colors.TIMELINE_BG)

      val startX = absoluteTimeToX(Instant.Zero)
      val endX = absoluteTimeToX(timeline.getLength)

      shapeDrawer.filledRectangle(startX, 0, endX - startX, getHeight, Colors.TIMELINE_OVERLAY)
    }

    private[tlarea] def drawTicks(): Unit = {
      val rawInterval = view.durationTime * (TimelineRenderer.PIXELS_PER_TICK / getWidth)
      val interval = Timespan(niceScale(rawInterval.value))
      val start = view.startTime.floorTo(interval)

      var t = start
      while (t < view.startTime + view.durationTime) {
        val x = absoluteTimeToX(t)
        shapeDrawer.filledRectangle(x, 0, 1, getHeight, Colors.TIMELINE_OVERLAY)
        t += interval
      }
    }

    private[tlarea] def drawTrackBands(): Unit = {
      val top = getHeight + view.trackYShift
      var i = 0
      var done = false
      while (!done) {
        val y = top - i * view.trackHeight
        if (y <= -view.trackHeight) {
          done = true
        } else {
          shapeDrawer.filledRectangle(0, y - view.trackHeight, getWidth, view.trackHeight, Colors.TIMELINE_OVERLAY)
          i += 2
        }
      }
    }

    private[tlarea] def drawPlayhead(): Unit = {
      val x = absoluteTimeToX(playhead.getTime)

      shapeDrawer.filledTriangle(
        x - 10, getHeight,
        x + 10, getHeight,
        x, getHeight - 20,
        Color.RED
      )

      shapeDrawer.line(x, 0, x, getHeight, Color.RED, 3)
    }
  }

  private[tlarea] object TimelineRenderer {
    private[tlarea] val PIXELS_PER_TICK: Float = 200f
  }
}

object TimelineView {
  private val KEY_HORIZONTAL_SPEED: Float = 1200f
  private val KEY_VERTICAL_SPEED: Float = 1200f

  enum Actions(displayName0: String, defaultKeys0: Int*) extends ShortcutAction {
    case SCROLL_LEFT extends Actions(("向左滚动".in(Zh) | "Scroll Left".in(En)).str(), A)
    case SCROLL_RIGHT extends Actions(("向右滚动".in(Zh) | "Scroll Right".in(En)).str(), D)
    case SCROLL_UP extends Actions(("向上滚动".in(Zh) | "Scroll Up".in(En)).str(), W)
    case SCROLL_DOWN extends Actions(("向下滚动".in(Zh) | "Scroll Down".in(En)).str(), S)
    case SPLIT extends Actions(("分割".in(Zh) | "Split".in(En)).str(), Q)
    case DELETE extends Actions(("删除".in(Zh) | "Delete".in(En)).str(), X)
    case UNDO extends Actions(("撤销".in(Zh) | "Undo".in(En)).str(), CONTROL_LEFT, Z)
    case REDO extends Actions(("重做".in(Zh) | "Redo".in(En)).str(), CONTROL_LEFT, Y)
    case PLAY_PAUSE extends Actions(("播放/暂停".in(Zh) | "Play/Pause".in(En)).str(), SPACE)
    case GROUP extends Actions(("分组".in(Zh) | "Group".in(En)).str(), F)
    case MARQUEE_SELECT extends Actions(("框选".in(Zh) | "Marquee Select".in(En)).str(), CONTROL_LEFT)
    case SEEK extends Actions(("定位播放头".in(Zh) | "Position Playhead".in(En)).str(), ALT_LEFT)
    case SNAP_IGNORE extends Actions(("忽略吸附".in(Zh) | "Ignore Snapping".in(En)).str(), CONTROL_LEFT)
    case COPY extends Actions(("复制".in(Zh) | "Copy".in(En)).str(), CONTROL_LEFT, C)
    case PASTE extends Actions(("粘贴".in(Zh) | "Paste".in(En)).str(), CONTROL_LEFT, V)

    override def displayName(): String = displayName0

    override def defaultKeys(): Array[Int] = defaultKeys0.toArray
  }

  private[tlarea] class ViewState {
    private[tlarea] var startTime: Instant = Instant.Zero
    private[tlarea] var durationTime: Timespan = Timespan.Zero
    private[tlarea] var trackHeight: Float = 0
    private[tlarea] var trackYShift: Float = 0

    private[tlarea] def timeToX(time: Instant, width: Float): Float = {
      (time - startTime).value.toFloat / durationTime.value * width
    }

    private[tlarea] def xToTime(x: Float, width: Float): Instant = {
      startTime + durationTime * (x.toDouble / width)
    }

    private[tlarea] def visibleInterval(): Interval = {
      startTime ~+ durationTime
    }

    private[tlarea] def zoom(amountY: Float, anchorXRatio: Float): Boolean = {
      val oldDuration = durationTime
      val scaleFactor = 1f + amountY * 0.1f
      if (scaleFactor <= 0f) {
        false
      } else {
        var newDuration = oldDuration * scaleFactor
        if (newDuration <= Timespan.SECOND) newDuration = Timespan.SECOND

        val anchorTime = startTime + oldDuration * anchorXRatio
        durationTime = newDuration
        startTime = Instant.Zero.max(anchorTime - newDuration * anchorXRatio)
        true
      }
    }

    private[tlarea] def scrollHorizontal(deltaPixels: Float, width: Float): Unit = {
      startTime = Instant.Zero.max(startTime + durationTime * (deltaPixels.toDouble / width))
    }

    private[tlarea] def scrollVertical(delta: Float): Unit = {
      trackYShift = Math.max(0, trackYShift + delta)
    }

    private[tlarea] def adjustTrackHeight(delta: Float): Unit = {
      trackHeight = Math.max(trackHeight + delta, 10)
    }
  }
}
