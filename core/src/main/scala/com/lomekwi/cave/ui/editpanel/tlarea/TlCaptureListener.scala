package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener

import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.timeline.{Gap, Interval, Segment, Track}


import scala.jdk.CollectionConverters.*
import java.util

/** 时间线捕获阶段监听器：处理框选与空白区播放头 seek。 */
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
      val onSource: Boolean = trackIndex >= 0 && trackIndex < timelineView.timeline.getTrackCount && {
        timelineView.timeline.getTrackOrCreate(trackIndex).get(timelineView.xToAbsoluteTime(x)) match {
          case _: Segment => true
          case _: Gap | null => false
        }
      }
      if (!onSource) {
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
    val lastTrack: Int = Math.min(timelineView.timeline.getTrackCount - 1, timelineView.yToTrackIndex(minY))

    val toSelect: util.Set[Source[?]] = new util.HashSet[Source[?]]()
    var i = firstTrack
    while (i <= lastTrack) {
      val track: Track = timelineView.timeline.getTrackOrCreate(i)
      val trackTop: Float = timelineView.trackIndexToTopY(i)
      val trackBottom: Float = trackTop - timelineView.view.trackHeight

      if (!(trackTop <= minY || trackBottom >= maxY)) {
        var rangeStartTime: Long = timelineView.xToAbsoluteTime(minX)
        var rangeEndTime: Long = timelineView.xToAbsoluteTime(maxX)
        if (rangeStartTime > rangeEndTime) {
          val t = rangeStartTime
          rangeStartTime = rangeEndTime
          rangeEndTime = t
        }

        val timeRange: Interval = Interval(rangeStartTime, rangeEndTime)
        for (element <- track.getIntersecting(timeRange).asScala) {
          element match {
            case Segment(source) =>
              val r = track.getRange(source)
              val sourceLeft: Float = timelineView.absoluteTimeToX(r.lo)
              val sourceRight: Float = timelineView.absoluteTimeToX(r.hi)

              if (sourceRight > minX && sourceLeft < maxX) {
                val group = timelineView.timeline.getGroup(source)
                if (group != null) {
                  toSelect.addAll(group)
                } else {
                  toSelect.add(source)
                }
              }
            case _: Gap =>
          }
        }
      }
      i += 1
    }

    if (!toSelect.isEmpty) {
      timelineView.selectSources(toSelect)
    }
  }
}
