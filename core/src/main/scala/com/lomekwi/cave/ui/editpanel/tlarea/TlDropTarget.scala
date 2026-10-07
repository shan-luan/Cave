package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop

import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.{Content, Segment}
import com.lomekwi.cave.util.MimeType
import com.lomekwi.cave.timeline.Interval
import com.lomekwi.cave.timeline.~~

import java.io.File
import java.io.IOException
import java.util

import scala.util.Using
import scala.jdk.CollectionConverters.*

/** 时间线拖放目标，接收拖入的文件并落地为片段。 */
class TlDropTarget(private final val timelineView: TimelineView) extends DragAndDrop.Target(timelineView) {

  override def drag(source: DragAndDrop.Source, payload: DragAndDrop.Payload, x: Float, y: Float, pointer: Int): Boolean = {
    payload.getObject match {
      case file: File =>
        val mimeType = MimeType.detectMimeType(file)
        App.mediaFactory.isSupported(mimeType)
      case _ => false
    }
  }

  override def drop(source: DragAndDrop.Source, payload: DragAndDrop.Payload, x: Float, y: Float, pointer: Int): Unit = {
    try {
      val file: File = payload.getObject.asInstanceOf[File]
      val segments: util.List[Content] = timelineView.project.sourceFactory.getAll(file)
      val startTime: Long = timelineView.xToAbsoluteTime(x)
      val baseTrack: Int = timelineView.yToTrackIndex(y)
      var trackOffset: Int = 0
      val added: util.List[Segment] = new util.ArrayList[Segment]()
      Using.resource(timelineView.timeline.record()) { h =>
        for (segment <- segments.asScala) {
          val duration: Long = segment.getDefaultDuration
          if (duration > 0) {
            var targetTrack: Int = baseTrack + trackOffset
            val range: Interval = startTime ~~ (startTime + duration)
            // 落点与既有内容重叠只要合法就接受，重叠会成为转场；放不下才换到下一条轨道
            var placed = false
            while (!placed) {
              val track = timelineView.timeline.getTrackOrCreate(targetTrack)
              if (timelineView.timeline.tryAdd(track, segment, range, startTime) == 0) {
                placed = true
              } else {
                targetTrack += 1
              }
            }
            trackOffset = targetTrack - baseTrack + 1
            added.add(segment)
          }
        }
      }
      if (added.size() >= 2) {
        val group = timelineView.timeline.newGroup()
        for (segment <- added.asScala) {
          group.add(segment)
        }
      }
      timelineView.dirty = true
    } catch {
      case e: IOException =>
        Gdx.app.error("TlDropTarget", "拖拽文件失败: " + e.getMessage)
    }
  }
}
