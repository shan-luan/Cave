package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop

import com.lomekwi.cave.app.App
import com.lomekwi.cave.timeline.{Content, Interval, Segment}
import com.lomekwi.cave.util.MimeType
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given
import com.lomekwi.cave.timeline.~~

import java.io.File
import java.io.IOException

import scala.collection.mutable
import scala.util.Using

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
      val segments: Vector[Content] = timelineView.context.sourceFactory.getAll(file)
      val startTime: Long = timelineView.xToAbsoluteTime(x)
      val baseTrack: Int = timelineView.yToTrackIndex(y)
      var trackOffset: Int = 0
      val added = mutable.ArrayBuffer.empty[Segment]
      Using.resource(timelineView.context.record()) { h =>
        for (segment <- segments) {
          val duration: Long = segment.getDefaultDuration
          if (duration > 0) {
            var targetTrack: Int = baseTrack + trackOffset
            val interval: Interval = startTime ~~ (startTime + duration)
            // 落点与既有内容重叠只要合法就接受，重叠会成为转场；放不下才换到下一条轨道
            var placed = false
            while (!placed) {
              val before = timelineView.context.timeline
              val next = timelineView.context.add(before.getTrackOrCreate(targetTrack), segment, interval, startTime)
              if (next ne before) {
                placed = true
              } else {
                targetTrack += 1
              }
            }
            trackOffset = targetTrack - baseTrack + 1
            added += segment
          }
        }
      }
      if (added.size >= 2) {
        val (t, group) = timelineView.context.timeline.newGroup()
        timelineView.context.applyTimeline(t.replaceGroup(group, group.addAll(added.map(_.id))))
      }
      timelineView.dirty = true
    } catch {
      case e: IOException =>
        Gdx.app.error("TlDropTarget", ("拖拽文件失败: ".in(Zh) | "Failed to drop file: ".in(En)).str() + e.getMessage)
    }
  }
}
