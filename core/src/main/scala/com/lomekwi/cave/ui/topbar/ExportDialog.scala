package com.lomekwi.cave.ui.topbar

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.kotcrab.vis.ui.widget.*
import com.kotcrab.vis.ui.widget.spinner.{IntSpinnerModel, SimpleFloatSpinnerModel, Spinner}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.project.{Project, ProjectSession}
import com.lomekwi.cave.resource.media.VdoRes
import com.lomekwi.cave.task.{ExportOptions, ExportOptionsSet, ExportPresetsChangedEvent, VideoExportTask}
import com.lomekwi.cave.ui.widget.FileChooserField
import com.lomekwi.cave.util.Units.MEGA
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent

import scala.compiletime.uninitialized

class ExportDialog(private val context: ProjectSession) extends VisDialog(("导出视频".in(Zh) | "Export Video".in(En)).str()) {
  private var fileChooserField: FileChooserField = uninitialized
  private var widthModel: IntSpinnerModel = uninitialized
  private var heightModel: IntSpinnerModel = uninitialized
  private var fpsModel: SimpleFloatSpinnerModel = uninitialized
  private var bitrateModel: SimpleFloatSpinnerModel = uninitialized
  private var presetSet: ExportOptionsSet = uninitialized

  {
    presetSet = ExportOptionsSet.load()

    val content: Table = getContentTable

    val form: VisTable = new VisTable()

    fileChooserField = new FileChooserField(
      ("选择导出位置...".in(Zh) | "Choose Export Location...".in(En)).str(),
      NativeFileChooserIntent.SAVE
    )

    val pathRow: VisTable = new VisTable()
    pathRow.add(new VisLabel(("输出文件".in(Zh) | "Output File".in(En)).str())).left().padRight(8)
    pathRow.add(fileChooserField).growX()
    form.add(pathRow).growX().pad(4).row()

    val dims: Array[Int] = detectDimensions()
    widthModel = new IntSpinnerModel(dims(0), 1, 7680, 1)
    heightModel = new IntSpinnerModel(dims(1), 1, 4320, 1)

    val dimRow: VisTable = new VisTable()
    dimRow.add(new VisLabel(("宽度".in(Zh) | "Width".in(En)).str())).left().padRight(8)
    dimRow.add(new Spinner("", widthModel)).width(100).padRight(16)
    dimRow.add(new VisLabel(("高度".in(Zh) | "Height".in(En)).str())).left().padRight(8)
    dimRow.add(new Spinner("", heightModel)).width(100)
    form.add(dimRow).growX().pad(4).row()

    fpsModel = new SimpleFloatSpinnerModel(30f, 1, 120, 1, 1)

    val fpsRow: VisTable = new VisTable()
    fpsRow.add(new VisLabel(("帧率".in(Zh) | "Frame Rate".in(En)).str())).left().padRight(8)
    fpsRow.add(new Spinner("", fpsModel)).width(100)
    fpsRow.add(new VisLabel("fps")).padLeft(4)
    form.add(fpsRow).growX().pad(4).row()

    bitrateModel = new SimpleFloatSpinnerModel(6f, 0.1f, 100, 0.5f, 1)

    val bitrateRow: VisTable = new VisTable()
    bitrateRow.add(new VisLabel(("码率".in(Zh) | "Bitrate".in(En)).str())).left().padRight(8)
    bitrateRow.add(new Spinner("", bitrateModel)).width(100)
    bitrateRow.add(new VisLabel("Mbps")).padLeft(4)
    form.add(bitrateRow).growX().pad(4).row()

    val scrollPane: VisScrollPane = new VisScrollPane(form)
    scrollPane.setFadeScrollBars(false)
    content.add(scrollPane).size(500, 280).row()

    if (presetSet.current().width > 0) {
      applyOptions(presetSet.current())
    }

    addCloseButton()

    val exportBtn: VisTextButton = new VisTextButton(("导出".in(Zh) | "Export".in(En)).str())
    exportBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        startExport()
      }
    })

    val btnRow: VisTable = new VisTable()
    btnRow.add(exportBtn)
    content.add(btnRow).padTop(12).row()

    val presetRow: VisTable = new VisTable()
    val presetLabel: VisLabel = new VisLabel(presetLabelText())
    val prevBtn: VisTextButton = new VisTextButton("-")
    val saveBtn: VisTextButton = new VisTextButton(("保存预设".in(Zh) | "Save Preset".in(En)).str())
    val nextBtn: VisTextButton = new VisTextButton("+")

    prevBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        presetSet.prev()
        applyOptions(presetSet.current())
        presetLabel.setText(presetLabelText())
      }
    })

    nextBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        presetSet.next()
        applyOptions(presetSet.current())
        presetLabel.setText(presetLabelText())
      }
    })

    saveBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        val opts: ExportOptions = collectOptions()
        presetSet.presets.add(opts)
        presetSet.currentIndex = presetSet.presets.size() - 1
        presetSet.save()
        presetLabel.setText(presetLabelText())
        applyOptions(opts)
        App.root.toastManager.show(
          ("已保存预设 #".in(Zh) | "Saved preset #".in(En)).str() + (presetSet.currentIndex + 1), 1.5f)
      }
    })

    val delBtn: VisTextButton = new VisTextButton(("删除".in(Zh) | "Delete".in(En)).str())
    delBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        if (presetSet.presets.size() <= 1) {
          App.root.toastManager.show(("至少保留一个预设".in(Zh) | "Keep at least one preset".in(En)).str(), 1.5f)
        } else {
          presetSet.presets.remove(presetSet.currentIndex)
          if (presetSet.currentIndex >= presetSet.presets.size()) {
            presetSet.currentIndex = presetSet.presets.size() - 1
          }
          presetSet.save()
          presetLabel.setText(presetLabelText())
          applyOptions(presetSet.current())
          App.root.toastManager.show(("已删除预设".in(Zh) | "Preset deleted".in(En)).str(), 1.5f)
        }
      }
    })

    presetRow.add(new VisLabel(("预设".in(Zh) | "Preset".in(En)).str())).padRight(8)
    presetRow.add(prevBtn).width(40).padRight(4)
    presetRow.add(presetLabel).padRight(4)
    presetRow.add(nextBtn).width(40).padRight(12)
    presetRow.add(saveBtn)
    presetRow.add(delBtn).padLeft(4)
    content.add(presetRow).padTop(8).row()
  }

  private def detectDimensions(): Array[Int] = {
    context.project.resources.valuesIterator.flatMap(_.iterator)
      .collectFirst { case v: VdoRes => Array(v.width, v.height) }
      .getOrElse(Array(1920, 1080))
  }

  private def presetLabelText(): String = {
    s"${presetSet.currentIndex + 1}/${presetSet.presets.size()}"
  }

  private def collectOptions(): ExportOptions = {
    new ExportOptions(
      fileChooserField.getPath,
      widthModel.getValue,
      heightModel.getValue,
      fpsModel.getValue,
      (bitrateModel.getValue * MEGA).toInt
    )
  }

  private def applyOptions(opts: ExportOptions): Unit = {
    if (opts.width == 0) {
      App.root.toastManager.show(("此预设为空，请先保存".in(Zh) | "This preset is empty, please save one first".in(En)).str(), 2f)
    } else {
      fileChooserField.setPath(opts.outputPath)
      widthModel.setValue(opts.width)
      heightModel.setValue(opts.height)
      fpsModel.setValue(opts.fps.toFloat)
      bitrateModel.setValue(opts.getBitrateMbps.toFloat)
    }
  }

  private def startExport(): Unit = {
    val path: String = fileChooserField.getPath
    if (path.isEmpty) {
      App.root.toastManager.show(("请选择输出文件".in(Zh) | "Please choose an output file".in(En)).str(), 2f)
    } else {
      val width: Int = widthModel.getValue
      val height: Int = heightModel.getValue
      val fps: Double = fpsModel.getValue.toDouble
      val bitrate: Int = (bitrateModel.getValue * MEGA).toInt

      val snapshot: Project = Project.dump(context.project)
      val task = new VideoExportTask(
        snapshot.timeline,
        snapshot,
        new java.io.File(path),
        width, height, fps, bitrate
      )
      App.taskPool.submit(task)
      App.root.toastManager.show(("开始导出：".in(Zh) | "Starting export: ".in(En)).str() + new java.io.File(path).getName, 2f)
      fadeOut()
    }
  }

  override def remove(): Boolean = {
    App.appEventBus.post(ExportPresetsChangedEvent)
    super.remove()
  }
}
