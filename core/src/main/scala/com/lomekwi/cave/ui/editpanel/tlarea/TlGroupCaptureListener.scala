package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener

import com.google.common.collect.Range

import com.lomekwi.cave.app.App
import com.lomekwi.cave.timeline.Segment
import com.lomekwi.cave.timeline.SegmentSelectedEvent
import com.lomekwi.cave.timeline.SegmentSetSelectedEvent
import com.lomekwi.cave.timeline.Track

import java.util.HashSet
import java.util.Set

import scala.jdk.CollectionConverters.*

/** 时间线捕获阶段监听器 —— 处理框选与空白区播放头 seek。 */
class TlGroupCaptureListener(private final val tlGroup: TlGroup) extends InputListener {

  override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
    if (button != Input.Buttons.LEFT) return false

    if (App.shortcutManager.isActive(TlGroup.Actions.MARQUEE_SELECT)) {
      tlGroup.marqueeActive = true
      tlGroup.marqueeStartX = x
      tlGroup.marqueeStartY = y
      tlGroup.marqueeEndX = x
      tlGroup.marqueeEndY = y
      return true
    }

    val trackIndex: Int = tlGroup.yToTrackIndex(y)
    val onSegment: Boolean = trackIndex >= 0 && trackIndex < tlGroup.timeline.getTracks().size()
      && tlGroup.timeline.getTrack(trackIndex).get(tlGroup.xToAbsoluteTime(x)) != null
    if (!onSegment) {
      tlGroup.playhead.seek(Math.max(tlGroup.xToAbsoluteTime(x), 0))
    }
    false
  }

  override def touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
    if (!tlGroup.marqueeActive) return
    tlGroup.marqueeEndX = x
    tlGroup.marqueeEndY = y
    event.stop()
  }

  override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
    if (!tlGroup.marqueeActive) return
    tlGroup.marqueeActive = false
    event.stop()

    val minX: Float = Math.min(tlGroup.marqueeStartX, tlGroup.marqueeEndX)
    val maxX: Float = Math.max(tlGroup.marqueeStartX, tlGroup.marqueeEndX)
    val minY: Float = Math.min(tlGroup.marqueeStartY, tlGroup.marqueeEndY)
    val maxY: Float = Math.max(tlGroup.marqueeStartY, tlGroup.marqueeEndY)

    if (maxX - minX < 2 || maxY - minY < 2) return

    val firstTrack: Int = Math.max(0, tlGroup.yToTrackIndex(maxY))
    val lastTrack: Int = Math.min(tlGroup.timeline.getTracks().size() - 1, tlGroup.yToTrackIndex(minY))

    val toSelect: Set[Segment] = new HashSet[Segment]()
    var i = firstTrack
    while (i <= lastTrack) {
      val track: Track = tlGroup.timeline.getTrack(i)
      val trackTop: Float = tlGroup.trackIndexToTopY(i)
      val trackBottom: Float = trackTop - tlGroup.view.trackHeight

      if (!(trackTop <= minY || trackBottom >= maxY)) {
        var segStartTime: Long = tlGroup.xToAbsoluteTime(minX)
        var segEndTime: Long = tlGroup.xToAbsoluteTime(maxX)
        if (segStartTime > segEndTime) {
          val t = segStartTime
          segStartTime = segEndTime
          segEndTime = t
        }

        val timeRange: Range[java.lang.Long] = Range.closedOpen(java.lang.Long.valueOf(segStartTime), java.lang.Long.valueOf(segEndTime))
        for (entry <- track.getSubRangeMapAsEntrySet(timeRange).asScala) {
          val seg: Segment = entry.getValue()
          val segLeft: Float = tlGroup.absoluteTimeToX(seg.getRange().lowerEndpoint())
          val segRight: Float = tlGroup.absoluteTimeToX(seg.getRange().upperEndpoint())

          if (segRight > minX && segLeft < maxX) {
            if (seg.getGroup() != null) {
              toSelect.addAll(seg.getGroup())
            } else {
              toSelect.add(seg)
            }
          }
        }
      }
      i += 1
    }

    if (!toSelect.isEmpty()) {
      tlGroup.clearSelection()
      for (seg <- toSelect.asScala) {
        tlGroup.selectedSegments.add(seg)
        seg.setSelected(true)
      }
      val count: Int = tlGroup.selectedSegments.size()
      if (count >= 2) {
        val e = new SegmentSelectedEvent(null, null, count)
        tlGroup.project.projEventBus.post(e)
        App.appEventBus.post(e)
        val ge = new SegmentSetSelectedEvent(tlGroup.selectedSegments, count)
        tlGroup.project.projEventBus.post(ge)
        App.appEventBus.post(ge)
      } else if (count == 1) {
        val seg: Segment = toSelect.iterator().next()
        val e = new SegmentSelectedEvent(seg, seg.getTrack(), 1)
        tlGroup.project.projEventBus.post(e)
        App.appEventBus.post(e)
      }
    }
  }
}
