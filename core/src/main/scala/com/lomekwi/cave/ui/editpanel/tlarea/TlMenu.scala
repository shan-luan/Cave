package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.PopupMenu
import com.lomekwi.cave.app.App
import com.lomekwi.cave.app.copy.PasteTemplate
import com.lomekwi.cave.pipeline.text.{TextFrame, TextSource}
import com.lomekwi.cave.timeline.{Boundless, Content, Interval, Segment}
import com.lomekwi.cave.timeline.~~
import com.lomekwi.cave.ui.listeners.ChangeListenerX
import com.lomekwi.cave.util.MimeType
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

import java.io.File
import java.io.IOException

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent

import scala.collection.mutable
import scala.compiletime.uninitialized

class TlMenu private[tlarea] (private final val timelineView: TimelineView) extends PopupMenu {
  private var time: Long = 0L

  {
    val addMenu: PopupMenu = new PopupMenu()
    addMenu.addItem(new MenuItem(("媒体源".in(Zh) | "Media Source".in(En)).str(), new ChangeListenerX(() => onAddMedia())))
    addMenu.addItem(new MenuItem(("文本源".in(Zh) | "Text Source".in(En)).str(), new ChangeListenerX(() => onAddText())))
    val addItem: MenuItem = new MenuItem(("新增...".in(Zh) | "Add...".in(En)).str())
    addItem.setSubMenu(addMenu)
    this.addItem(addItem)
  }

  def setContext(time: Long): Unit = {
    this.time = time
  }

  private def onAddMedia(): Unit = {
    val conf: NativeFileChooserConfiguration = new NativeFileChooserConfiguration()
    conf.title = ("选择媒体文件".in(Zh) | "Choose Media File".in(En)).str()
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
        Gdx.app.error("TlMenu", ("选择文件失败".in(Zh) | "Failed to choose file".in(En)).str(), exception)
      }
    })
  }

  private def onAddText(): Unit = {
    val segment: Content = new Boundless(timelineView.session.idGenerator.genId, new TextSource())
    val duration: Long = segment.getDefaultDuration

    val session = timelineView.session
    val interval: Interval = time ~~ (time + duration)
    session.snapshot()
    var targetTrack: Int = 0
    var placed = false
    while (!placed) {
      val before = session.timeline
      val next = before.add(before.getTrackOrCreate(targetTrack).index, segment, interval, time)(using session.idSource)
      if (next ne before) {
        session.applyProject(_.withTimeline(next))
        placed = true
      } else {
        targetTrack += 1
      }
    }

    timelineView.markTimelineDirty()
  }

  private def addMediaFile(file: File): Unit = {
    val session = timelineView.session
    try {
      val segments: Vector[Content] = session.sourceFactory.getAll(file)
      if (segments.nonEmpty) {
        val baseTrack: Int = 0
        var trackOffset: Int = 0
        val added = mutable.ArrayBuffer.empty[Segment]

        session.snapshot()
        for (segment <- segments) {
          val duration: Long = segment.getDefaultDuration
          if (duration > 0) {
            var targetTrack: Int = baseTrack + trackOffset
            val interval: Interval = time ~~ (time + duration)
            var placed = false
            while (!placed) {
              val before = session.timeline
              val next = before.add(before.getTrackOrCreate(targetTrack).index, segment, interval, time)(using session.idSource)
              if (next ne before) {
                session.applyProject(_.withTimeline(next))
                placed = true
              } else {
                targetTrack += 1
              }
            }

            trackOffset = targetTrack - baseTrack + 1
            added += segment
          }
        }

        if (added.size >= 2) {
          val (t, group) = session.timeline.newGroup()
          session.applyProject(_.withTimeline(t.replaceGroup(group, group.addAll(added.map(_.id)))))
        }

        timelineView.markTimelineDirty()
      }
    } catch {
      case e: IOException =>
        Gdx.app.error("TlMenu", ("添加媒体源失败: ".in(Zh) | "Failed to add media source: ".in(En)).str() + e.getMessage)
    }
  }
}
