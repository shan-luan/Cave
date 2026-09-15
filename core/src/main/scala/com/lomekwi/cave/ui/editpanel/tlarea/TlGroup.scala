package com.lomekwi.cave.ui.editpanel.tlarea

import com.lomekwi.cave.util.Units.{SECOND, niceScale}

import com.badlogic.gdx.{Gdx, Input}
import com.badlogic.gdx.graphics.{Color, Cursor}
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.{Group, InputEvent}
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.lomekwi.cave.app.shortcut.ShortcutAction
import com.lomekwi.cave.timeline.{Interval, Segment, SegmentGroup, SegmentSelectedEvent, SegmentSet, SegmentSetSelectedEvent, Timeline, UndoManager}
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

class TlGroup(project0: Project) extends Group with Focusable {

  private final val renderer: TimelineRenderer = new TimelineRenderer()
  final val segMenu: SegMenu = new SegMenu(this)
  final val tlGroupMenu: TlGroupMenu = new TlGroupMenu(this)

  private[tlarea] var timeline: Timeline = uninitialized
  private[tlarea] var playhead: Playhead = uninitialized
  private[tlarea] var project: Project = uninitialized

  private[tlarea] final val view: TlGroup.ViewState = new TlGroup.ViewState()

  /** 拖拽吸附时的吸附时间点，-1 表示无吸附（由 SegActor 拖拽时设置） */
  private[tlarea] var snapIndicatorTime: Long = -1

  private[tlarea] var dirty: Boolean = true
  private[tlarea] final val selectedSegments: SegmentSet = new SegmentSet()

  private final val pointer: Vector2 = new Vector2()

  private[tlarea] var marqueeActive: Boolean = false
  private[tlarea] var marqueeStartX: Float = 0
  private[tlarea] var marqueeStartY: Float = 0
  private[tlarea] var marqueeEndX: Float = 0
  private[tlarea] var marqueeEndY: Float = 0

  project = project0
  timeline = project.timeline
  playhead = project.playhead

  project.projEventBus.register(this)

  this.view.startTime = 0
  this.view.durationTime = Math.max(project.timeline.getLength, 30 * SECOND)
  this.view.trackHeight = 80

  addDefaultListeners()

  private def addDefaultListeners(): Unit = {
    addListener(new TlGroupInputListener(this))
    addCaptureListener(new TlGroupCaptureListener(this))
    App.root.getDragAndDrop.addTarget(new TlGroupDropTarget(this))
    addListener(new DragListener {
      setButton(Input.Buttons.MIDDLE)

      override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
        if (super.touchDown(event, x, y, pointer, button)) {
          Gdx.graphics.setSystemCursor(Cursor.SystemCursor.AllResize)
          return true
        }
        false
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

        if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_RIGHT)) {
          view.startTime += (TlGroup.KEY_HORIZONTAL_SPEED * delta * timePerPixel).toLong
          acted = true
        }
        if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_LEFT)) {
          view.startTime = Math.max(0, view.startTime - (TlGroup.KEY_HORIZONTAL_SPEED * delta * timePerPixel).toLong)
          acted = true
        }
        if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_DOWN)) {
          view.trackYShift = Math.max(0, view.trackYShift + TlGroup.KEY_VERTICAL_SPEED * delta)
          acted = true
        }
        if (App.shortcutManager.isActive(TlGroup.Actions.SCROLL_UP)) {
          view.trackYShift = Math.max(0, view.trackYShift - TlGroup.KEY_VERTICAL_SPEED * delta)
          acted = true
        }

        if (App.shortcutManager.isActive(TlGroup.Actions.SEEK)) {
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

        for (seg <- track.getIntersectingSegments(visibleRange).asScala) {
          val actor = seg.getActor
          val r = seg.getRange
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

  def selectSegment(segment: Segment, addToSelection: Boolean): Unit = {
    val group = segment.getGroup
    if (group != null) {
      if (addToSelection) {
        val anySelected = group.asScala.exists(s => selectedSegments.contains(s))
        if (anySelected) {
          for (s <- group.asScala) {
            selectedSegments.remove(s)
            s.setSelected(false)
          }
        } else {
          for (s <- group.asScala) {
            selectedSegments.add(s)
            s.setSelected(true)
          }
        }
      } else {
        clearSelection()
        for (s <- group.asScala) {
          selectedSegments.add(s)
          s.setSelected(true)
        }
      }
      val count = selectedSegments.size()
      if (count == 0) {
        val e = SegmentSelectedEvent(null, null, 0)
        project.projEventBus.post(e)
        App.appEventBus.post(e)
      } else if (count == 1) {
        val remaining = selectedSegments.iterator().next()
        val e = SegmentSelectedEvent(remaining, remaining.getTrack, 1)
        project.projEventBus.post(e)
        App.appEventBus.post(e)
      } else {
        val e = SegmentSelectedEvent(null, null, count)
        project.projEventBus.post(e)
        App.appEventBus.post(e)
        val ge = SegmentSetSelectedEvent(selectedSegments, count)
        project.projEventBus.post(ge)
        App.appEventBus.post(ge)
      }
      return
    }
    if (!addToSelection) {
      clearSelection()
    }
    if (selectedSegments.contains(segment)) {
      selectedSegments.remove(segment)
      segment.setSelected(false)
      val count = selectedSegments.size()
      if (count == 0) {
        val e = SegmentSelectedEvent(null, null, 0)
        project.projEventBus.post(e)
        App.appEventBus.post(e)
      } else if (count == 1) {
        val remaining = selectedSegments.iterator().next()
        val e = SegmentSelectedEvent(remaining, remaining.getTrack, 1)
        project.projEventBus.post(e)
        App.appEventBus.post(e)
      } else {
        val e = SegmentSelectedEvent(null, null, count)
        project.projEventBus.post(e)
        App.appEventBus.post(e)
        val ge = SegmentSetSelectedEvent(selectedSegments, count)
        project.projEventBus.post(ge)
        App.appEventBus.post(ge)
      }
    } else {
      selectedSegments.add(segment)
      segment.setSelected(true)
      val count = selectedSegments.size()
      if (count >= 2) {
        val e = SegmentSelectedEvent(null, null, count)
        project.projEventBus.post(e)
        App.appEventBus.post(e)
        val ge = SegmentSetSelectedEvent(selectedSegments, count)
        project.projEventBus.post(ge)
        App.appEventBus.post(ge)
      } else {
        val e = SegmentSelectedEvent(segment, segment.getTrack, 1)
        project.projEventBus.post(e)
        App.appEventBus.post(e)
      }
    }
  }

  def clearSelection(): Unit = {
    selectedSegments.setSelected(false)
    selectedSegments.clear()
    val e = SegmentSelectedEvent(null, null, 0)
    project.projEventBus.post(e)
    App.appEventBus.post(e)
  }

  private def selectSegments(segments: util.Collection[Segment]): Unit = {
    clearSelection()
    for (seg <- segments.asScala) {
      selectedSegments.add(seg)
      seg.setSelected(true)
    }
    val count = selectedSegments.size()
    if (count == 1) {
      val seg = selectedSegments.iterator().next()
      val e = SegmentSelectedEvent(seg, seg.getTrack, 1)
      project.projEventBus.post(e)
      App.appEventBus.post(e)
    } else if (count >= 2) {
      val e = SegmentSelectedEvent(null, null, count)
      project.projEventBus.post(e)
      App.appEventBus.post(e)
      val ge = SegmentSetSelectedEvent(selectedSegments, count)
      project.projEventBus.post(ge)
      App.appEventBus.post(ge)
    }
  }

  private[tlarea] def removeSeg(segActor: SegActor): Unit = {
    removeActor(segActor)
    val segment = segActor.getSegment
    Using.resource(timeline.record()) { h =>
      timeline.remove(segment)
    }
    dirty = true
  }

  /** 右键菜单“分割”入口。 */
  private[tlarea] def split(segActor: SegActor, time: Long): Unit = {
    splitSegment(segActor.getSegment, time)
    dirty = true
  }

  /** 快捷键分割入口：按当前鼠标位置定位分割点。 */
  private[tlarea] def splitAtCursor(): Unit = {
    val stage = getStage
    if (stage == null) return
    val local = stageToLocalCoordinates(
      stage.screenToStageCoordinates(pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)))
    val trackIndex = yToTrackIndex(local.y)
    val time = xToAbsoluteTime(local.x)
    val track = timeline.getTrack(trackIndex)
    val segment = track.get(time)
    if (segment == null) return
    splitSegment(segment, time)
    dirty = true
  }

  private def splitSegment(segment: Segment, time: Long): Unit = {
    val group = segment.getGroup
    val segments: util.List[Segment] = if (group != null) util.List.copyOf(group) else util.List.of(segment)
    val beforeSegments: util.List[Segment] = new util.ArrayList[Segment]()
    val afterSegments: util.List[Segment] = new util.ArrayList[Segment]()
    var splitAny = false
    Using.resource(timeline.record()) { h =>
      for (member <- segments.asScala) {
        val range = member.getRange
        val start: Long = range.lo
        val end: Long = range.hi
        if (time > start && time < end) {
          val track = member.getTrack
          timeline.split(track, time)
          beforeSegments.add(member)
          afterSegments.add(track.get(time))
          splitAny = true
        } else if (end <= time) {
          beforeSegments.add(member)
        } else {
          afterSegments.add(member)
        }
      }
    }
    if (splitAny && group != null) {
      for (member <- segments.asScala) group.remove(member)
      if (beforeSegments.size() >= 2) TlGroup.regroup(beforeSegments)
      if (afterSegments.size() >= 2) TlGroup.regroup(afterSegments)
    }
  }

  private def deleteAtCursor(): Unit = {
    val stage = getStage
    if (stage == null) return
    val local = stageToLocalCoordinates(
      stage.screenToStageCoordinates(pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)))
    val trackIndex = yToTrackIndex(local.y)
    val track = timeline.getTrack(trackIndex)
    val segment = track.get(xToAbsoluteTime(local.x))
    if (segment == null) return
    Using.resource(timeline.record()) { h =>
      timeline.remove(segment)
    }
    dirty = true
  }

  private[tlarea] def deleteSelected(): Unit = {
    if (selectedSegments.isEmpty) {
      deleteAtCursor()
      return
    }
    val segments: util.List[Segment] = util.List.copyOf(selectedSegments)
    clearSelection()
    Using.resource(timeline.record()) { h =>
      timeline.remove(segments)
    }
    dirty = true
  }

  private[tlarea] def groupSelectedSegments(): Unit = {
    if (selectedSegments.size() < 2) return

    val anyInGroup = selectedSegments.asScala.exists(seg => seg.getGroup != null)

    if (anyInGroup) {
      val savedState: util.Map[Segment, SegmentGroup] = new util.HashMap[Segment, SegmentGroup]()
      val affectedGroups: util.Set[SegmentGroup] = new util.HashSet[SegmentGroup]()
      for (seg <- selectedSegments.asScala) {
        val g = seg.getGroup
        if (g != null) {
          savedState.put(seg, g)
          affectedGroups.add(g)
        }
      }
      val dissolvedMembers: util.Map[SegmentGroup, util.Set[Segment]] = new util.HashMap[SegmentGroup, util.Set[Segment]]()
      for (g <- affectedGroups.asScala) {
        dissolvedMembers.put(g, new util.HashSet[Segment](g))
      }

      for (seg <- selectedSegments.asScala) {
        val g = seg.getGroup
        if (g != null) {
          g.remove(seg)
        }
      }
      for (g <- affectedGroups.asScala) {
        if (g.size() < 2) {
          for (s <- new util.HashSet[Segment](g).asScala) {
            g.remove(s)
          }
        }
      }

      project.undoManager.record(new UndoManager.UndoableCommand {
        override def undo(): Unit = {
          for (e <- dissolvedMembers.entrySet().asScala) {
            e.getKey.addAll(e.getValue)
          }
          for (e <- savedState.entrySet().asScala) {
            val seg = e.getKey
            val g = e.getValue
            if (g != null && !g.contains(seg)) {
              g.add(seg)
            }
          }
          dirty = true
        }

        override def redo(): Unit = {
          for (seg <- savedState.keySet().asScala) {
            val g = seg.getGroup
            if (g != null) {
              g.remove(seg)
            }
          }
          for (e <- dissolvedMembers.entrySet().asScala) {
            val g = e.getKey
            if (g.size() < 2) {
              for (s <- new util.HashSet[Segment](g).asScala) {
                g.remove(s)
              }
            }
          }
          dirty = true
        }
      })
    } else {
      val group = new SegmentGroup()
      val segs: util.List[Segment] = new util.ArrayList[Segment](selectedSegments)
      group.addAll(segs)

      project.undoManager.record(new UndoManager.UndoableCommand {
        override def undo(): Unit = {
          for (seg <- segs.asScala) {
            group.remove(seg)
          }
          dirty = true
        }

        override def redo(): Unit = {
          group.addAll(segs)
          dirty = true
        }
      })
    }
  }
//FIXME:跨项目粘贴
  private[tlarea] def performPaste(): Unit = {
    val clip = App.copyManager.getClipboard
    if (clip == null) return

    val s = getStage
    if (s == null) return
    val local = stageToLocalCoordinates(
      s.screenToStageCoordinates(pointer.set(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)))

    val baseTime = Math.max(xToAbsoluteTime(local.x), 0)
    val baseTrack = Math.max(yToTrackIndex(local.y), 0)

    var pasted: util.List[Segment] = null
    clip match {
      case templateGroup: SegmentGroup => pasted = pasteGroup(templateGroup, baseTime, baseTrack)
      case templateSet: SegmentSet => pasted = pasteSet(templateSet, baseTime, baseTrack)
      case template: Segment => pasted = pasteSegment(template, baseTime, baseTrack)
      case _ => pasted = util.List.of[Segment]()
    }

    if (!pasted.isEmpty) {
      selectSegments(pasted)
    }

    App.copyManager.refreshClipboard()
  }

  private def pasteSegment(template: Segment, time: Long, baseTrack: Int): util.List[Segment] = {
    val duration = template.getRange.hi - template.getRange.lo
    if (duration <= 0) return util.List.of[Segment]()

    var track = timeline.getTrack(baseTrack)
    var range = Interval(time, time + duration)
    var trackIndex = baseTrack
    while (!track.isFree(range, util.Set.of[Segment]())) {
      trackIndex += 1
      track = timeline.getTrack(trackIndex)
      range = Interval(time, time + duration)
    }

    template.setOrigin(time + template.getOrigin - template.getRange.lo)

    Using.resource(timeline.record()) { h =>
      timeline.tryAdd(track, template, Interval(time, time + duration))
    }
    markTimelineDirty()
    util.List.of(template)
  }

  private def pasteGroup(template: SegmentGroup, baseTime: Long, baseTrack: Int): util.List[Segment] = {
    val pasted = new util.ArrayList[Segment]()

    val sorted = new util.ArrayList[Segment](template)
    sorted.sort(util.Comparator.comparingInt[Segment]((s: Segment) => s.getTrack.index))

    val minTrack = sorted.get(0).getTrack.index
    val minStart = sorted.stream().mapToLong((s: Segment) => s.getRange.lo).min().orElse(baseTime)
    val timeOffset = baseTime - minStart

    Using.resource(timeline.record()) { h =>
      for (seg <- sorted.asScala) {
        val duration = seg.getRange.hi - seg.getRange.lo
        if (duration > 0) {
          val trackOffset = seg.getTrack.index - minTrack
          var ti = baseTrack + trackOffset
          var track = timeline.getTrack(ti)
          val segStart = seg.getRange.lo + timeOffset
          var range = Interval(segStart, segStart + duration)
          while (!track.isFree(range, util.Set.of[Segment]())) {
            ti += 1
            track = timeline.getTrack(ti)
            range = Interval(segStart, segStart + duration)
          }

          seg.setOrigin(seg.getOrigin + timeOffset)

          timeline.tryAdd(track, seg, Interval(segStart, segStart + duration))
          pasted.add(seg)
        }
      }
    }

    markTimelineDirty()
    pasted
  }

  private def pasteSet(template: SegmentSet, baseTime: Long, baseTrack: Int): util.List[Segment] = {
    val pasted = new util.ArrayList[Segment]()

    val sorted = new util.ArrayList[Segment](template)
    sorted.sort(util.Comparator.comparingInt[Segment]((s: Segment) => s.getTrack.index))

    val minTrack = sorted.get(0).getTrack.index
    val minStart = sorted.stream().mapToLong((s: Segment) => s.getRange.lo).min().orElse(baseTime)
    val timeOffset = baseTime - minStart

    Using.resource(timeline.record()) { h =>
      for (seg <- sorted.asScala) {
        val duration = seg.getRange.hi - seg.getRange.lo
        if (duration > 0) {
          val trackOffset = seg.getTrack.index - minTrack
          var ti = baseTrack + trackOffset
          var track = timeline.getTrack(ti)
          val segStart = seg.getRange.lo + timeOffset
          var range = Interval(segStart, segStart + duration)
          while (!track.isFree(range, util.Set.of[Segment]())) {
            ti += 1
            track = timeline.getTrack(ti)
            range = Interval(segStart, segStart + duration)
          }

          seg.setOrigin(seg.getOrigin + timeOffset)

          timeline.tryAdd(track, seg, Interval(segStart, segStart + duration))
          pasted.add(seg)
        }
      }
    }

    markTimelineDirty()
    pasted
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

object TlGroup {
  private final val KEY_HORIZONTAL_SPEED: Float = 1200f
  private final val KEY_VERTICAL_SPEED: Float = 1200f

  private def regroup(members: util.Collection[Segment]): SegmentGroup = {
    val group = new SegmentGroup()
    group.addAll(members)
    group
  }

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
      if (scaleFactor <= 0f) return false

      var newDuration = (oldDuration * scaleFactor).toLong
      if (newDuration <= SECOND) newDuration = SECOND

      val anchorTime = startTime + (anchorXRatio * oldDuration).toLong
      durationTime = newDuration
      startTime = Math.max(anchorTime - (anchorXRatio * newDuration).toLong, 0)
      true
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
