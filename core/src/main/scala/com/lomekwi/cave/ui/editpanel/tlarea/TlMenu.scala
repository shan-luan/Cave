package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.PopupMenu
import com.lomekwi.cave.app.App
import com.lomekwi.cave.app.copy.PasteTemplate
import com.lomekwi.cave.pipeline.{Content, Segment}
import com.lomekwi.cave.pipeline.text.{TextFrame, TextSource}
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.timeline.Interval
import com.lomekwi.cave.timeline.~~
import com.lomekwi.cave.ui.listeners.ChangeListenerX
import com.lomekwi.cave.util.MimeType

import java.io.File
import java.io.IOException
import java.util

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent

import scala.compiletime.uninitialized
import scala.util.Using
import scala.jdk.CollectionConverters.*

class TlMenu private[tlarea] (private final val timelineView: TimelineView) extends PopupMenu {
  private var time: Long = 0L
  private var pasteItem: MenuItem = uninitialized

  {
    val addMenu: PopupMenu = new PopupMenu()
    addMenu.addItem(new MenuItem("媒体源", new ChangeListenerX(() => onAddMedia())))
    addMenu.addItem(new MenuItem("文本源", new ChangeListenerX(() => onAddText())))
    val addItem: MenuItem = new MenuItem("新增...")
    addItem.setSubMenu(addMenu)
    this.addItem(addItem)

    pasteItem = new MenuItem("粘贴", new ChangeListenerX(() => timelineView.performPaste()))
    this.addItem(pasteItem)
  }

  def setContext(time: Long): Unit = {
    this.time = time
    pasteItem.setDisabled(!App.copyManager.getClipboard.isInstanceOf[PasteTemplate])
  }

  private def onAddMedia(): Unit = {
    val conf: NativeFileChooserConfiguration = new NativeFileChooserConfiguration()
    conf.title = "选择媒体文件"
    conf.intent = NativeFileChooserIntent.OPEN
    if (Gdx.app.getType == Application.ApplicationType.Android) {
      conf.mimeFilter = "*/*"
    } else {
      conf.nameFilter = (dir: File, name: String) => {
        val mime = MimeType.detectMimeType(new File(dir, name))
        mime != null && App.mediaFactory.isSupported(mime)
      }
    }
    App.fileChooser.chooseFile(conf, new NativeFileChooserCallback {
      override def onFileChosen(file: FileHandle): Unit = {
        addMediaFile(file.file())
      }

      override def onCancellation(): Unit = {}

      override def onError(exception: Exception): Unit = {
        Gdx.app.error("TlMenu", "选择文件失败", exception)
      }
    })
  }

  private def onAddText(): Unit = {
    val segment: Segment[?] = new Content[TextFrame](new TextSource())
    val duration: Long = segment.getDefaultDuration

    var targetTrack: Int = 0
    val range: Interval = time ~~ (time + duration)
    while (!timelineView.getTimeline.getTrackOrCreate(targetTrack).isFree(range, util.Set.of[Segment[?]]())) {
      targetTrack += 1
    }

    val timeline = timelineView.getTimeline
    Using.resource(timeline.record()) { h =>
      timeline.tryAdd(timeline.getTrackOrCreate(targetTrack), segment, range, time)
    }

    timelineView.markTimelineDirty()
  }

  private def addMediaFile(file: File): Unit = {
    val project: Project = timelineView.getProject
    try {
      val segments: util.List[Segment[?]] = project.sourceFactory.getAll(file)
      if (!segments.isEmpty) {
        val baseTrack: Int = 0
        var trackOffset: Int = 0
        val added: util.List[Segment[?]] = new util.ArrayList[Segment[?]]()

        val timeline = timelineView.getTimeline
        Using.resource(timeline.record()) { h =>
          for (segment <- segments.asScala) {
            val duration: Long = segment.getDefaultDuration
            if (duration > 0) {
              var targetTrack: Int = baseTrack + trackOffset
              val range: Interval = time ~~ (time + duration)
              while (!timeline.getTrackOrCreate(targetTrack).isFree(range, util.Set.of[Segment[?]]())) {
                targetTrack += 1
              }

              timeline.tryAdd(timeline.getTrackOrCreate(targetTrack), segment, range, time)
              trackOffset = targetTrack - baseTrack + 1
              added.add(segment)
            }
          }
        }

        if (added.size() >= 2) {
          val group = timeline.newGroup()
          for (segment <- added.asScala) {
            group.add(segment)
          }
        }

        timelineView.markTimelineDirty()
      }
    } catch {
      case e: IOException =>
        Gdx.app.error("TlMenu", "添加媒体源失败: " + e.getMessage)
    }
  }
}
