package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener

import com.lomekwi.cave.app.App
import com.lomekwi.cave.timeline.{Interval, Segment}
import com.lomekwi.cave.timeline.SegmentSelectedEvent
import com.lomekwi.cave.timeline.SegmentSetSelectedEvent
import com.lomekwi.cave.timeline.Track


import scala.jdk.CollectionConverters.*
import java.util

/** 时间线捕获阶段监听器 —— 处理框选与空白区播放头 seek。 */
class TlCaptureListener(private final val timelineView: TimelineView) extends InputListener {

  override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
    if (button != Input.Buttons.LEFT) {
      false
    } else if (App.shortcutManager.isActive(TimelineView.Actions.MARQUEE_SELECT)) {
      timelineView.marqueeActive = true
      timelineView.marqueeStartX = x
      timelineView.marqueeStartY = y
      timelineView.marqueeEndX = x
      timelineView.marqueeEndY = y
      true
    } else {
      val trackIndex: Int = timelineView.yToTrackIndex(y)
      val onSegment: Boolean = trackIndex >= 0 && trackIndex < timelineView.timeline.getTracks.size()
        && timelineView.timeline.getTrack(trackIndex).get(timelineView.xToAbsoluteTime(x)) != null
      if (!onSegment) {
        timelineView.playhead.seek(Math.max(timelineView.xToAbsoluteTime(x), 0))
      }
      false
    }
  }

  override def touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
    if (timelineView.marqueeActive) {
      timelineView.marqueeEndX = x
      timelineView.marqueeEndY = y
      event.stop()
    }
  }

  override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
    if (!timelineView.marqueeActive) return
    timelineView.marqueeActive = false
    event.stop()

    val minX: Float = Math.min(timelineView.marqueeStartX, timelineView.marqueeEndX)
    val maxX: Float = Math.max(timelineView.marqueeStartX, timelineView.marqueeEndX)
    val minY: Float = Math.min(timelineView.marqueeStartY, timelineView.marqueeEndY)
    val maxY: Float = Math.max(timelineView.marqueeStartY, timelineView.marqueeEndY)

    if (maxX - minX < 2 || maxY - minY < 2) return

    val firstTrack: Int = Math.max(0, timelineView.yToTrackIndex(maxY))
    val lastTrack: Int = Math.min(timelineView.timeline.getTracks.size() - 1, timelineView.yToTrackIndex(minY))

    val toSelect: util.Set[Segment] = new util.HashSet[Segment]()
    var i = firstTrack
    while (i <= lastTrack) {
      val track: Track = timelineView.timeline.getTrack(i)
      val trackTop: Float = timelineView.trackIndexToTopY(i)
      val trackBottom: Float = trackTop - timelineView.view.trackHeight

      if (!(trackTop <= minY || trackBottom >= maxY)) {
        var segStartTime: Long = timelineView.xToAbsoluteTime(minX)
        var segEndTime: Long = timelineView.xToAbsoluteTime(maxX)
        if (segStartTime > segEndTime) {
          val t = segStartTime
          segStartTime = segEndTime
          segEndTime = t
        }

        val timeRange: Interval = Interval(segStartTime, segEndTime)
        for (seg <- track.getIntersectingSegments(timeRange).asScala) {
          val segLeft: Float = timelineView.absoluteTimeToX(seg.getRange.lo)
          val segRight: Float = timelineView.absoluteTimeToX(seg.getRange.hi)

          if (segRight > minX && segLeft < maxX) {
            if (seg.getGroup != null) {
              toSelect.addAll(seg.getGroup)
            } else {
              toSelect.add(seg)
            }
          }
        }
      }
      i += 1
    }

    if (!toSelect.isEmpty) {
      timelineView.clearSelection()
      for (seg <- toSelect.asScala) {
        timelineView.selectedSegments.add(seg)
        seg.setSelected(true)
      }
      val count: Int = timelineView.selectedSegments.size()
      if (count >= 2) {
        val e = SegmentSelectedEvent(null, null, count)
        timelineView.project.projEventBus.post(e)
        App.appEventBus.post(e)
        val ge = SegmentSetSelectedEvent(timelineView.selectedSegments, count)
        timelineView.project.projEventBus.post(ge)
        App.appEventBus.post(ge)
      } else if (count == 1) {
        val seg: Segment = toSelect.iterator().next()
        val e = SegmentSelectedEvent(seg, seg.getTrack, 1)
        timelineView.project.projEventBus.post(e)
        App.appEventBus.post(e)
      }
    }
  }
}
