package com.lomekwi.cave.ui.editpanel.tlarea

import com.lomekwi.cave.util.Units.{SECOND, niceScale}

import com.badlogic.gdx.{Gdx, Input}
import com.badlogic.gdx.graphics.{Color, Cursor}
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.{Group, InputEvent}
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.lomekwi.cave.app.copy.PasteTemplate
import com.lomekwi.cave.app.selection.{SourceSet, SourceSetSelectedEvent}
import com.lomekwi.cave.app.shortcut.ShortcutAction
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.timeline.{Gap, Interval, Segment, SourceGroup, Timeline, Track, UndoManager}
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.timeline.playback.Playhead

import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.{Colors, Focusable}


import space.earlygrey.shapedrawer.ShapeDrawer

import scala.compiletime.uninitialized
import scala.util.Using
import scala.jdk.CollectionConverters.*

import com.badlogic.gdx.Input.Keys.*
import java.util

class TimelineView(project0: Project) extends Group with Focusable {

  private final val renderer: TimelineRenderer = new TimelineRenderer()
  final val srcMenu: TlSrcMenu = new TlSrcMenu(this)
  final val viewMenu: TlMenu = new TlMenu(this)

  private[tlarea] var timeline: Timeline = uninitialized
  private[tlarea] var playhead: Playhead = uninitialized
  private[tlarea] var project: Project = uninitialized

  private[tlarea] final val view: TimelineView.ViewState = new TimelineView.ViewState()

  /** 拖拽吸附时的吸附时间点，-1 表示无吸附（由 TlSrcActor 拖拽时设置） */
  private[tlarea] var snapIndicatorTime: Long = -1

  private[tlarea] var dirty: Boolean = true
  private[tlarea] var selectedSources: SourceSet = uninitialized

  private final val pointer: Vector2 = new Vector2()

  private[tlarea] var marqueeActive: Boolean = false
  private[tlarea] var marqueeStartX: Float = 0
  private[tlarea] var marqueeStartY: Float = 0
  private[tlarea] var marqueeEndX: Float = 0
  private[tlarea] var marqueeEndY: Float = 0

  project = project0
  timeline = project.timeline
  playhead = project.playhead
  selectedSources = new SourceSet(timeline)

  project.projEventBus.register(this)

  this.view.startTime = 0
  this.view.durationTime = Math.max(project.timeline.getLength, 30 * SECOND)
  this.view.trackHeight = 80

  addDefaultListeners()

  private def addDefaultListeners(): Unit = {
    addListener(new TlInputListener(this))
    addCaptureListener(new TlCaptureListener(this))
    App.root.getDragAndDrop.addTarget(new TlDropTarget(this))
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
        val timePerPixel: Float = view.durationTime.toFloat / getWidth

        if (App.shortcutManager.isActive(TimelineView.Actions.SCROLL_RIGHT)) {
          view.startTime += (TimelineView.KEY_HORIZONTAL_SPEED * delta * timePerPixel).toLong
          acted = true
        }
        if (App.shortcutManager.isActive(TimelineView.Actions.SCROLL_LEFT)) {
          view.startTime = Math.max(0, view.startTime - (TimelineView.KEY_HORIZONTAL_SPEED * delta * timePerPixel).toLong)
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
    }

    // dirty 时按模型重建 UI；所有 Actor（含拖拽中）都是模型的纯投影：
    // 拖拽只改模型并置 dirty，不做手动摆位，拖拽目标只依赖鼠标轨迹与按下锚点。
    if (dirty) {
      clearChildren(false)

      val visibleRange = view.visibleRange()
      for (i <- timeline.getTracks.asScala.indices.reverse) {
        val track = timeline.getTracks.get(i)

        for (element <- track.getIntersecting(visibleRange).asScala) {
          element match {
            case Segment(source) =>
              val actor = source.getTlSrcActor
              actor.tl = this
              val r = track.getRange(source)
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
            case _: Gap =>
          }
        }
      }

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
    if (snapIndicatorTime >= 0) {
      val x = absoluteTimeToX(snapIndicatorTime)
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
    project.projEventBus.unregister(this)
  }

  private[tlarea] def absoluteTimeToX(time: Long): Float = {
    view.timeToX(time, getWidth)
  }

  private[tlarea] def xToAbsoluteTime(x: Float): Long = {
    view.xToTime(x, getWidth)
  }

  private[tlarea] def seekPlayheadAtX(x: Float): Unit = {
    playhead.seek(Math.max(xToAbsoluteTime(x), 0))
  }

  /** 单选/追加选择。选中的是组则整组一起切换。 */
  def selectSource(source: Source[?], addToSelection: Boolean): Unit = {
    val group = timeline.getGroup(source)
    if (group != null) {
      if (addToSelection) {
        val anySelected = group.asScala.exists(s => selectedSources.contains(s))
        for (s <- group.asScala) {
          if (anySelected) selectedSources.remove(s) else selectedSources.add(s)
        }
      } else {
        selectedSources.clear()
        for (s <- group.asScala) {
          selectedSources.add(s)
        }
      }
    } else if (!addToSelection) {
      selectedSources.clear()
      selectedSources.add(source)
    } else if (selectedSources.contains(source)) {
      selectedSources.remove(source)
    } else {
      selectedSources.add(source)
    }
    publishSelection()
  }

  /** 整体替换选中集。 */
  private[tlarea] def selectSources(sources: util.Collection[Source[?]]): Unit = {
    selectedSources.clear()
    for (source <- sources.asScala) {
      selectedSources.add(source)
    }
    publishSelection()
  }

  def clearSelection(): Unit = {
    selectedSources.clear()
    publishSelection()
  }

  private def publishSelection(): Unit = {
    val e = SourceSetSelectedEvent(selectedSources, selectedSources.size())
    project.projEventBus.post(e)
    App.appEventBus.post(e)
  }

  private[tlarea] def removeSource(srcActor: TlSrcActor): Unit = {
    removeActor(srcActor)
    val source = srcActor.getSource
    Using.resource(timeline.record()) { h =>
      timeline.remove(source)
    }
    dirty = true
  }

  /** 右键菜单“分割”入口。 */
  private[tlarea] def split(srcActor: TlSrcActor, time: Long): Unit = {
    splitSource(srcActor.getSource, time)
    dirty = true
  }

  /** 快捷键分割入口：按当前鼠标位置定位分割点。 */
  private[tlarea] def splitAtCursor(): Unit = {
    val stage = getStage
    if (stage != null) {
      val local = stageToLocalCoordinates(
        stage.screenToStageCoordinates(pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)))
      val track = timeline.getTrack(yToTrackIndex(local.y))
      track.get(xToAbsoluteTime(local.x)) match {
        case Segment(source) =>
          splitSource(source, xToAbsoluteTime(local.x))
          dirty = true
        case _: Gap | null =>
      }
    }
  }

  private def splitSource(source: Source[?], time: Long): Unit = {
    val track = timeline.findTrackOf(source)
    if (track == null) return
    val group = timeline.getGroup(source)
    val members: util.List[Source[?]] = if (group != null) util.List.copyOf(group) else util.List.of(source)
    val beforeSources: util.List[Source[?]] = new util.ArrayList[Source[?]]()
    val afterSources: util.List[Source[?]] = new util.ArrayList[Source[?]]()
    var splitAny = false
    Using.resource(timeline.record()) { h =>
      for (member <- members.asScala) {
        val memberTrack = timeline.findTrackOf(member)
        val range = memberTrack.getRange(member)
        val start: Long = range.lo
        val end: Long = range.hi
        if (time > start && time < end) {
          timeline.split(memberTrack, time)
          beforeSources.add(member)
          memberTrack.get(time) match {
            case Segment(right) => afterSources.add(right)
            case _: Gap | null =>
          }
          splitAny = true
        } else if (end <= time) {
          beforeSources.add(member)
        } else {
          afterSources.add(member)
        }
      }
    }
    if (splitAny && group != null) {
      for (member <- members.asScala) group.remove(member)
      timeline.dropGroup(group)
      if (beforeSources.size() >= 2) regroup(beforeSources)
      if (afterSources.size() >= 2) regroup(afterSources)
    }
  }

  private def deleteAtCursor(): Unit = {
    val stage = getStage
    if (stage != null) {
      val local = stageToLocalCoordinates(
        stage.screenToStageCoordinates(pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)))
      val track = timeline.getTrack(yToTrackIndex(local.y))
      track.get(xToAbsoluteTime(local.x)) match {
        case Segment(source) =>
          Using.resource(timeline.record()) { h =>
            timeline.remove(source)
          }
          dirty = true
        case _: Gap | null =>
      }
    }
  }

  private[tlarea] def deleteSelected(): Unit = {
    if (selectedSources.isEmpty) {
      deleteAtCursor()
    } else {
      val sources: util.List[Source[?]] = util.List.copyOf(selectedSources)
      clearSelection()
      Using.resource(timeline.record()) { h =>
        timeline.remove(sources)
      }
      dirty = true
    }
  }

  /** 选中的源里但凡有已分组的就先解散，否则把它们合成一组。 */
  private[tlarea] def groupSelectedSources(): Unit = {
    if (selectedSources.size() < 2) return

    val anyInGroup = selectedSources.asScala.exists(source => timeline.getGroup(source) != null)

    if (anyInGroup) {
      val savedState: util.Map[Source[?], SourceGroup] = new util.HashMap[Source[?], SourceGroup]()
      val affectedGroups: util.Set[SourceGroup] = new util.HashSet[SourceGroup]()
      for (source <- selectedSources.asScala) {
        val group = timeline.getGroup(source)
        if (group != null) {
          savedState.put(source, group)
          affectedGroups.add(group)
        }
      }
      val dissolvedMembers: util.Map[SourceGroup, util.Set[Source[?]]] = new util.HashMap[SourceGroup, util.Set[Source[?]]]()
      for (group <- affectedGroups.asScala) {
        dissolvedMembers.put(group, new util.HashSet[Source[?]](group))
      }

      def dissolve(): Unit = {
        for (source <- selectedSources.asScala) {
          val group = timeline.getGroup(source)
          if (group != null) {
            group.remove(source)
          }
        }
        for (group <- affectedGroups.asScala) {
          if (group.size() < 2) {
            for (s <- new util.HashSet[Source[?]](group).asScala) {
              group.remove(s)
            }
            timeline.dropGroup(group)
          }
        }
      }

      dissolve()

      project.undoManager.record(new UndoManager.UndoableCommand {
        override def undo(): Unit = {
          for (e <- dissolvedMembers.entrySet().asScala) {
            timeline.adoptGroup(e.getKey)
            e.getKey.addAll(e.getValue)
          }
          for (e <- savedState.entrySet().asScala) {
            val source = e.getKey
            val group = e.getValue
            if (group != null && !group.contains(source)) {
              group.add(source)
            }
          }
          dirty = true
        }

        override def redo(): Unit = {
          dissolve()
          dirty = true
        }
      })
    } else {
      val group = timeline.newGroup()
      val sources: util.List[Source[?]] = new util.ArrayList[Source[?]](selectedSources)
      group.addAll(sources)

      project.undoManager.record(new UndoManager.UndoableCommand {
        override def undo(): Unit = {
          for (source <- sources.asScala) {
            group.remove(source)
          }
          timeline.dropGroup(group)
          dirty = true
        }

        override def redo(): Unit = {
          timeline.adoptGroup(group)
          group.addAll(sources)
          dirty = true
        }
      })
    }
  }
//FIXME:跨项目粘贴
  private[tlarea] def performPaste(): Unit = {
    val template = App.copyManager.getClipboard
    val s = getStage
    if (template != null && s != null) {
      val local = stageToLocalCoordinates(
        s.screenToStageCoordinates(pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)))

      val baseTime = Math.max(xToAbsoluteTime(local.x), 0)
      val baseTrack = Math.max(yToTrackIndex(local.y), 0)

      val pasted: util.List[Source[?]] = template match {
        case template: PasteTemplate => pasteTemplate(template, baseTime, baseTrack)
        case _ => util.List.of[Source[?]]()
      }

      if (!pasted.isEmpty) {
        selectSources(pasted)
      }

      App.copyManager.refreshClipboard()
    }
  }

  /** 把剪贴板模板整批放进时间轴：保持成员相对间距，冲突时整组顺移轨道。 */
  private def pasteTemplate(template: PasteTemplate, baseTime: Long, baseTrack: Int): util.List[Source[?]] = {
    val entries = template.getEntries
    if (entries.isEmpty) return util.List.of[Source[?]]()

    val pasted = new util.ArrayList[Source[?]](entries.size())

    val sorted = new util.ArrayList[PasteTemplate.Entry](entries)
    sorted.sort(util.Comparator.comparingInt[PasteTemplate.Entry]((e: PasteTemplate.Entry) => e.track.index))

    val minTrack = sorted.get(0).track.index
    val minStart = sorted.stream().mapToLong((e: PasteTemplate.Entry) => e.range.lo).min().orElse(baseTime)
    val timeOffset = baseTime - minStart

    // 模板里的组要登记进本时间线，粘贴后的源才查得到自己的组
    for (entry <- entries.asScala) {
      if (entry.group != null) {
        timeline.adoptGroup(entry.group)
      }
    }

    Using.resource(timeline.record()) { h =>
      for (entry <- sorted.asScala) {
        val duration = entry.range.hi - entry.range.lo
        if (duration > 0) {
          val trackOffset = entry.track.index - minTrack
          var ti = baseTrack + trackOffset
          var track = timeline.getTrack(ti)
          val start = entry.range.lo + timeOffset
          var range = Interval(start, start + duration)
          while (!track.isFree(range, util.Set.of[Source[?]]())) {
            ti += 1
            track = timeline.getTrack(ti)
            range = Interval(start, start + duration)
          }

          timeline.tryAdd(track, entry.source, range, entry.origin + timeOffset)
          pasted.add(entry.source)
        }
      }
    }

    markTimelineDirty()
    pasted
  }

  /** 把一组成员合成新组。 */
  private def regroup(members: util.Collection[Source[?]]): SourceGroup = {
    val group = timeline.newGroup()
    group.addAll(members)
    group
  }

  private[tlarea] def yToTrackIndex(y: Float): Int = {
    val top = getHeight + view.trackYShift
    val distance = top - y
    Math.max(0f, Math.floor(distance / view.trackHeight)).toInt
  }

  private[tlarea] def trackIndexToTopY(index: Int): Float = {
    getHeight + view.trackYShift - index * view.trackHeight
  }

  def getProject: Project = {
    project
  }

  def getTimeline: Timeline = {
    timeline
  }

  def markTimelineDirty(): Unit = {
    dirty = true
  }

  override def sizeChanged(): Unit = {
    dirty = true
  }

  private[tlarea] class TimelineRenderer {
    private[tlarea] final val shapeDrawer: ShapeDrawer = App.root.getShapeDrawer

    private[tlarea] def drawBackground(): Unit = {
      shapeDrawer.filledRectangle(0, 0, getWidth, getHeight, Colors.TIMELINE_BG)

      val startX = absoluteTimeToX(0)
      val endX = absoluteTimeToX(timeline.getLength)

      shapeDrawer.filledRectangle(startX, 0, endX - startX, getHeight, Colors.TIMELINE_OVERLAY)
    }

    private[tlarea] def drawTicks(): Unit = {
      val interval = niceScale((view.durationTime * TimelineRenderer.PIXELS_PER_TICK / getWidth).toLong)
      val start = (view.startTime / interval) * interval

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
    private[tlarea] final val PIXELS_PER_TICK: Float = 200f
  }
}

object TimelineView {
  private final val KEY_HORIZONTAL_SPEED: Float = 1200f
  private final val KEY_VERTICAL_SPEED: Float = 1200f

  enum Actions(displayName0: String, defaultKeys0: Int*) extends ShortcutAction {
    case SCROLL_LEFT extends Actions("向左滚动", A)
    case SCROLL_RIGHT extends Actions("向右滚动", D)
    case SCROLL_UP extends Actions("向上滚动", W)
    case SCROLL_DOWN extends Actions("向下滚动", S)
    case SPLIT extends Actions("分割", Q)
    case DELETE extends Actions("删除", X)
    case UNDO extends Actions("撤销", CONTROL_LEFT, Z)
    case REDO extends Actions("重做", CONTROL_LEFT, Y)
    case PLAY_PAUSE extends Actions("播放/暂停", SPACE)
    case GROUP extends Actions("分组", F)
    case MARQUEE_SELECT extends Actions("框选", CONTROL_LEFT)
    case SEEK extends Actions("定位播放头", E)
    case SNAP_IGNORE extends Actions("忽略吸附", CONTROL_LEFT)
    case COPY extends Actions("复制", CONTROL_LEFT, C)
    case PASTE extends Actions("粘贴", CONTROL_LEFT, V)

    override def displayName(): String = displayName0

    override def defaultKeys(): Array[Int] = defaultKeys0.toArray
  }

  private[tlarea] class ViewState {
    private[tlarea] var startTime: Long = 0
    private[tlarea] var durationTime: Long = 0
    private[tlarea] var trackHeight: Float = 0
    private[tlarea] var trackYShift: Float = 0

    private[tlarea] def timeToX(time: Long, width: Float): Float = {
      (time - startTime).toFloat / durationTime * width
    }

    private[tlarea] def xToTime(x: Float, width: Float): Long = {
      startTime + ((x / width) * durationTime).toLong
    }

    private[tlarea] def visibleRange(): Interval = {
      Interval(startTime, startTime + durationTime)
    }

    private[tlarea] def zoom(amountY: Float, anchorXRatio: Float): Boolean = {
      val oldDuration = durationTime
      val scaleFactor = 1f + amountY * 0.1f
      if (scaleFactor <= 0f) {
        false
      } else {
        var newDuration = (oldDuration * scaleFactor).toLong
        if (newDuration <= SECOND) newDuration = SECOND

        val anchorTime = startTime + (anchorXRatio * oldDuration).toLong
        durationTime = newDuration
        startTime = Math.max(anchorTime - (anchorXRatio * newDuration).toLong, 0)
        true
      }
    }

    private[tlarea] def scrollHorizontal(deltaPixels: Float, width: Float): Unit = {
      startTime = Math.max(startTime + (xToTime(deltaPixels, width) - xToTime(0, width)), 0)
    }

    private[tlarea] def scrollVertical(delta: Float): Unit = {
      trackYShift = Math.max(0, trackYShift + delta)
    }

    private[tlarea] def adjustTrackHeight(delta: Float): Unit = {
      trackHeight = Math.max(trackHeight + delta, 10)
    }
  }
}
