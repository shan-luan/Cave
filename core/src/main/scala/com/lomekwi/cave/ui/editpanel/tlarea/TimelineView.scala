package com.lomekwi.cave.ui.editpanel.tlarea

import com.lomekwi.cave.util.Units.{SECOND, niceScale}

import com.badlogic.gdx.{Gdx, Input}
import com.badlogic.gdx.graphics.{Color, Cursor}
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.{Group, InputEvent}
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.lomekwi.cave.app.copy.PasteTemplate
import com.lomekwi.cave.app.selection.{SegmentSet, SegmentSetSelectedEvent}
import com.lomekwi.cave.app.shortcut.ShortcutAction
import com.lomekwi.cave.pipeline.{Gap, Segment, Transition}
import com.lomekwi.cave.timeline.{Interval, SegmentGroup, Timeline, Track, UndoManager}
import com.lomekwi.cave.timeline.~~
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.playback.Playhead

import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.{Colors, Focusable}


import space.earlygrey.shapedrawer.ShapeDrawer

import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.util.Using

import com.badlogic.gdx.Input.Keys.*

class TimelineView(project0: Project) extends Group with Focusable {

  private final val renderer: TimelineRenderer = new TimelineRenderer()
  final val srcMenu: TlSegmentMenu = new TlSegmentMenu(this)
  final val viewMenu: TlMenu = new TlMenu(this)

  private[tlarea] var timeline: Timeline = uninitialized
  private[tlarea] var playhead: Playhead = uninitialized
  private[tlarea] var project: Project = uninitialized

  private[tlarea] final val view: TimelineView.ViewState = new TimelineView.ViewState()

  /** 拖拽吸附时的吸附时间点，-1 表示无吸附（由 [[TlSegmentActor]] 拖拽时设置） */
  private[tlarea] var snapIndicatorTime: Long = -1

  private[tlarea] var dirty: Boolean = true
  private[tlarea] var selectedSegments: SegmentSet = uninitialized

  private final val pointer: Vector2 = new Vector2()

  private final val inputListener: TlInputListener = new TlInputListener(this)
  private final val captureListener: TlCaptureListener = new TlCaptureListener(this)

  /** 当前有拖拽会话的片段，拖拽中每帧由 [[act]] 驱动 [[TlSegmentActor.dragTo]]；转场拖到消失被移出舞台时仍需驱动 */
  private[tlarea] var draggingActor: TlSegmentActor = uninitialized

  /** [[TimelineView.Actions.SEEK]] 快捷键按住时的刷动会话标志，按住期间播放头与拖拽刷动同样冻结在 Seeking */
  private var seekScrubbing: Boolean = false

  private[tlarea] var marqueeActive: Boolean = false
  private[tlarea] var marqueeStartX: Float = 0
  private[tlarea] var marqueeStartY: Float = 0
  private[tlarea] var marqueeEndX: Float = 0
  private[tlarea] var marqueeEndY: Float = 0

  project = project0
  timeline = project.timeline
  playhead = project.playhead
  selectedSegments = new SegmentSet(timeline)

  project.projEventBus.register(this)

  this.view.startTime = 0
  this.view.durationTime = Math.max(project.timeline.getLength, 30 * SECOND)
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

      // SEEK 按住与拖拽刷动走同一套会话，两者叠加时靠 Playhead 的会话计数保持冻结，全部结束才恢复
      if (App.shortcutManager.isActive(TimelineView.Actions.SEEK)) {
        if (!seekScrubbing) {
          playhead.beginScrub()
          seekScrubbing = true
        }
      } else if (seekScrubbing) {
        playhead.endScrub()
        seekScrubbing = false
      }

      // 拖拽会话每帧驱动一次回调，视图在拖拽期间滚动缩放时目标仍跟手。
      // 视图状态可能已被上面的滚动改变，鼠标位置要重算，不能复用本次开头的 pointer
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

    // dirty 时按模型重建 UI，所有 Actor（含拖拽中）都是模型的纯投影。
    // 拖拽只改模型并置 dirty，不做手动摆位，拖拽目标只依赖鼠标轨迹与按下锚点。
    if (dirty) {
      clearChildren(false)

      val visibleRange = view.visibleRange()
      for (i <- timeline.getTracks.indices.reverse) {
        val track = timeline.getTracks(i)

        // 转场排在最后添加，压在两侧内容之上，重叠区里鼠标命中的是转场
        val entries = track.getIntersecting(visibleRange).sortBy {
          case _: Transition => 1
          case _ => 0
        }
        for (s <- entries) {
          val actor = s.getTlSegmentActor
          actor.tl = this
          val r = track.getRange(s)
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
  def selectSegment(segment: Segment, addToSelection: Boolean): Unit = {
    val group = timeline.getGroup(segment)
    if (group != null) {
      if (addToSelection) {
        val anySelected = group.exists(s => selectedSegments.contains(s))
        for (s <- group) {
          if (anySelected) selectedSegments.remove(s) else selectedSegments.add(s)
        }
      } else {
        selectedSegments.clear()
        for (s <- group) {
          selectedSegments.add(s)
        }
      }
    } else if (!addToSelection) {
      selectedSegments.clear()
      selectedSegments.add(segment)
    } else if (selectedSegments.contains(segment)) {
      selectedSegments.remove(segment)
    } else {
      selectedSegments.add(segment)
    }
    publishSelection()
  }

  /** 整体替换选中集。 */
  private[tlarea] def selectSegments(segments: Iterable[Segment]): Unit = {
    selectedSegments.clear()
    for (segment <- segments) {
      selectedSegments.add(segment)
    }
    publishSelection()
  }

  def clearSelection(): Unit = {
    selectedSegments.clear()
    publishSelection()
  }

  private def publishSelection(): Unit = {
    val e = SegmentSetSelectedEvent(selectedSegments, selectedSegments.size)
    project.projEventBus.post(e)
    App.appEventBus.post(e)
  }

  private[tlarea] def removeSegment(segmentActor: TlSegmentActor): Unit = {
    removeActor(segmentActor)
    val segment = segmentActor.segment
    Using.resource(timeline.record()) { h =>
      timeline.remove(segment)
    }
    dirty = true
  }

  /** 右键菜单“分割”入口。 */
  private[tlarea] def split(segmentActor: TlSegmentActor, time: Long): Unit = {
    splitSegment(segmentActor.segment, time)
    dirty = true
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
          splitSegment(s, xToAbsoluteTime(local.x))
          dirty = true
        case _: Gap =>
      }
    }
  }

  private def splitSegment(segment: Segment, time: Long): Unit = {
    val track = timeline.findTrackOf(segment)
    if (track == null) return
    val group = timeline.getGroup(segment)
    val members: Seq[Segment] = if (group != null) group.toList else List(segment)
    val beforeSegments: mutable.ArrayBuffer[Segment] = mutable.ArrayBuffer.empty
    val afterSegments: mutable.ArrayBuffer[Segment] = mutable.ArrayBuffer.empty
    var splitAny = false
    Using.resource(timeline.record()) { h =>
      for (member <- members) {
        val memberTrack = timeline.findTrackOf(member)
        val range = memberTrack.getRange(member)
        val start: Long = range.lo
        val end: Long = range.hi
        if (time > start && time < end) {
          timeline.split(memberTrack, time)
          beforeSegments += member
          // 分割换上了新版本，右半段要从时间线现取，旧实例上还是整段
          timeline.getTrackOrCreate(memberTrack.index).get(time) match {
            case s: Segment => afterSegments += s
            case _: Gap =>
          }
          splitAny = true
        } else if (end <= time) {
          beforeSegments += member
        } else {
          afterSegments += member
        }
      }
    }
    if (splitAny && group != null) {
      for (member <- members) group.remove(member)
      timeline.dropGroup(group)
      if (beforeSegments.size >= 2) regroup(beforeSegments)
      if (afterSegments.size >= 2) regroup(afterSegments)
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
          Using.resource(timeline.record()) { h =>
            timeline.remove(s)
          }
          dirty = true
        case _: Gap =>
      }
    }
  }

  private[tlarea] def deleteSelected(): Unit = {
    if (selectedSegments.isEmpty) {
      deleteAtCursor()
    } else {
      val segments: Seq[Segment] = selectedSegments.toSeq
      clearSelection()
      Using.resource(timeline.record()) { h =>
        timeline.remove(segments)
      }
      dirty = true
    }
  }

  /** 选中的片段里但凡有已分组的就先解散，否则把它们合成一组。 */
  private[tlarea] def groupSelectedSegments(): Unit = {
    if (selectedSegments.size < 2) return

    val anyInGroup = selectedSegments.exists(segment => timeline.getGroup(segment) != null)

    if (anyInGroup) {
      val savedState: mutable.HashMap[Segment, SegmentGroup] = mutable.HashMap.empty
      val affectedGroups: mutable.LinkedHashSet[SegmentGroup] = mutable.LinkedHashSet.empty
      for (segment <- selectedSegments) {
        val group = timeline.getGroup(segment)
        if (group != null) {
          savedState.put(segment, group)
          affectedGroups.add(group)
        }
      }
      val dissolvedMembers: mutable.HashMap[SegmentGroup, mutable.LinkedHashSet[Segment]] = mutable.HashMap.empty
      for (group <- affectedGroups) {
        dissolvedMembers.put(group, mutable.LinkedHashSet.from(group))
      }

      def dissolve(): Unit = {
        for (segment <- selectedSegments) {
          val group = timeline.getGroup(segment)
          if (group != null) {
            group.remove(segment)
          }
        }
        for (group <- affectedGroups) {
          if (group.size < 2) {
            for (s <- mutable.LinkedHashSet.from(group)) {
              group.remove(s)
            }
            timeline.dropGroup(group)
          }
        }
      }

      dissolve()

      project.undoManager.record(new UndoManager.UndoableCommand {
        override def undo(): Unit = {
          for (e <- dissolvedMembers) {
            timeline.adoptGroup(e._1)
            e._1.addAll(e._2)
          }
          for (e <- savedState) {
            val segment = e._1
            val group = e._2
            if (group != null && !group.contains(segment)) {
              group.add(segment)
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
      val segments: mutable.ArrayBuffer[Segment] = mutable.ArrayBuffer.from(selectedSegments)
      group.addAll(segments)

      project.undoManager.record(new UndoManager.UndoableCommand {
        override def undo(): Unit = {
          for (segment <- segments) {
            group.remove(segment)
          }
          timeline.dropGroup(group)
          dirty = true
        }

        override def redo(): Unit = {
          timeline.adoptGroup(group)
          group.addAll(segments)
          dirty = true
        }
      })
    }
  }
//FIXME:跨项目粘贴
  private[tlarea] def performPaste(): Unit = {
    val template = App.copyManager.clipboard
    val s = getStage
    if (template != null && s != null) {
      val local = stageToLocalCoordinates(
        s.screenToStageCoordinates(pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)))

      val baseTime = Math.max(xToAbsoluteTime(local.x), 0)
      val baseTrack = Math.max(yToTrackIndex(local.y), 0)

      val pasted: mutable.ArrayBuffer[Segment] = template match {
        case template: PasteTemplate => pasteTemplate(template, baseTime, baseTrack)
        case _ => mutable.ArrayBuffer.empty
      }

      if (pasted.nonEmpty) {
        selectSegments(pasted)
      }

      App.copyManager.refreshClipboard()
    }
  }

  /** 把剪贴板模板整批放进时间轴，保持成员相对间距，冲突时整组顺移轨道。 */
  private def pasteTemplate(template: PasteTemplate, baseTime: Long, baseTrack: Int): mutable.ArrayBuffer[Segment] = {
    val entries = template.entries
    if (entries.isEmpty) return mutable.ArrayBuffer.empty

    val pasted = mutable.ArrayBuffer.empty[Segment]

    val sorted: mutable.ArrayBuffer[PasteTemplate.Entry] = mutable.ArrayBuffer.from(entries)
    sorted.sortInPlaceBy(_.trackIndex)

    val minTrack = sorted.head.trackIndex
    val minStart = if (sorted.isEmpty) baseTime else sorted.iterator.map(_.range.lo).min
    val timeOffset = baseTime - minStart

    // 模板里的组要登记进本时间线，粘贴后的片段才查得到自己的组
    for (entry <- entries) {
      if (entry.group != null) {
        timeline.adoptGroup(entry.group)
      }
    }

    Using.resource(timeline.record()) { h =>
      for (entry <- sorted) {
        val duration = entry.range.hi - entry.range.lo
        if (duration > 0) {
          val trackOffset = entry.trackIndex - minTrack
          var ti = baseTrack + trackOffset
          val start = entry.range.lo + timeOffset
          val range = start ~~ (start + duration)
          // 落点与既有内容重叠只要合法就接受，重叠会成为转场；
          // 只有确实放不下时才整条上移轨道重试，成员之间因此不会被拆散
          var placed = false
          while (!placed) {
            val track = timeline.getTrackOrCreate(ti)
            if (timeline.tryAdd(track, entry.segment, range, entry.origin + timeOffset) == 0) {
              placed = true
            } else {
              ti += 1
            }
          }

          pasted += entry.segment
        }
      }
    }

    markTimelineDirty()
    pasted
  }

  /** 把一组成员合成新组。 */
  private def regroup(members: Iterable[Segment]): SegmentGroup = {
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
    case SEEK extends Actions("定位播放头", ALT_LEFT)
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
      startTime ~~ (startTime + durationTime)
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
