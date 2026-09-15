package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.PopupMenu
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.text.TextSrc
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.timeline.{Interval, Segment}
import com.lomekwi.cave.timeline.SegmentGroup
import com.lomekwi.cave.timeline.SegmentSet
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

class TlGroupMenu private[tlarea] (private final val tlGroup: TlGroup) extends PopupMenu {
  private var time: Long = 0L
  private var pasteItem: MenuItem = uninitialized

  {
    val addMenu: PopupMenu = new PopupMenu()
    addMenu.addItem(new MenuItem("媒体片段", new ChangeListenerX(() => onAddMedia())))
    addMenu.addItem(new MenuItem("文本片段", new ChangeListenerX(() => onAddText())))
    val addItem: MenuItem = new MenuItem("新增...")
    addItem.setSubMenu(addMenu)
    this.addItem(addItem)

    pasteItem = new MenuItem("粘贴", new ChangeListenerX(() => tlGroup.performPaste()))
    this.addItem(pasteItem)
  }

  def setContext(time: Long): Unit = {
    this.time = time
    pasteItem.setDisabled(!(App.copyManager.getClipboard.isInstanceOf[Segment] || App.copyManager.getClipboard.isInstanceOf[SegmentGroup] || App.copyManager.getClipboard.isInstanceOf[SegmentSet]))
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
        Gdx.app.error("TlGroupMenu", "选择文件失败", exception)
      }
    })
  }

  private def onAddText(): Unit = {
    val seg: Segment = new Segment(new TextSrc())
    seg.setOrigin(time)
    val duration: Long = seg.getSource.getDefaultSegmentDuration

    var targetTrack: Int = 0
    val range: Interval = Interval(time, time + duration)
    while (!tlGroup.getTimeline.getTrack(targetTrack).isFree(range, util.Set.of[Segment]())) {
      targetTrack += 1
    }

    val timeline = tlGroup.getTimeline
    Using.resource(timeline.record()) { h =>
      timeline.tryAdd(timeline.getTrack(targetTrack), seg, range)
    }

    tlGroup.markTimelineDirty()
  }

  private def addMediaFile(file: File): Unit = {
    val project: Project = tlGroup.getProject
    try {
      val segments: util.List[Segment] = project.mediaSegFactory.getAll(file)
      if (!segments.isEmpty) {
        val baseTrack: Int = 0
        var trackOffset: Int = 0
        val added: util.List[Segment] = new util.ArrayList[Segment]()

        val timeline = tlGroup.getTimeline
        Using.resource(timeline.record()) { h =>
          for (seg <- segments.asScala) {
            seg.setOrigin(time)
            val duration: Long = seg.getSource.getDefaultSegmentDuration
            if (duration > 0) {
              var targetTrack: Int = baseTrack + trackOffset
              val range: Interval = Interval(time, time + duration)
              while (!timeline.getTrack(targetTrack).isFree(range, util.Set.of[Segment]())) {
                targetTrack += 1
              }

              timeline.tryAdd(timeline.getTrack(targetTrack), seg, range)
              trackOffset = targetTrack - baseTrack + 1
              added.add(seg)
            }
          }
        }

        if (added.size() >= 2) {
          val group = new SegmentGroup()
          for (seg <- added.asScala) {
            group.add(seg)
          }
        }

        tlGroup.markTimelineDirty()
      }
    } catch {
      case e: IOException =>
        Gdx.app.error("TlGroupMenu", "添加媒体片段失败: " + e.getMessage)
    }
  }
}
