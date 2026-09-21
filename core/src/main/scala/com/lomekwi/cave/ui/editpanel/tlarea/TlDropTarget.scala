package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop

import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.util.MimeType
import com.lomekwi.cave.timeline.Interval

import java.io.File
import java.io.IOException
import java.util

import scala.util.Using
import scala.jdk.CollectionConverters.*

/** 时间线拖放目标：接收拖入的文件并落地为源。 */
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
      val sources: util.List[Source[?]] = timelineView.project.mediaSegFactory.getAll(file)
      val startTime: Long = timelineView.xToAbsoluteTime(x)
      val baseTrack: Int = timelineView.yToTrackIndex(y)
      var trackOffset: Int = 0
      val added: util.List[Source[?]] = new util.ArrayList[Source[?]]()
      Using.resource(timelineView.timeline.record()) { h =>
        for (src <- sources.asScala) {
          val duration: Long = src.getDefaultDuration
          if (duration > 0) {
            var targetTrack: Int = baseTrack + trackOffset
            val range: Interval = Interval(startTime, startTime + duration)
            while (!timelineView.timeline.getTrackOrCreate(targetTrack).isFree(range, util.Set.of[Source[?]]())) {
              targetTrack += 1
            }
            timelineView.timeline.tryAdd(timelineView.timeline.getTrackOrCreate(targetTrack), src, range, startTime)
            trackOffset = targetTrack - baseTrack + 1
            added.add(src)
          }
        }
      }
      if (added.size() >= 2) {
        val group = timelineView.timeline.newGroup()
        for (src <- added.asScala) {
          group.add(src)
        }
      }
      timelineView.dirty = true
    } catch {
      case e: IOException =>
        Gdx.app.error("TlDropTarget", "拖拽文件失败: " + e.getMessage)
    }
  }
}
