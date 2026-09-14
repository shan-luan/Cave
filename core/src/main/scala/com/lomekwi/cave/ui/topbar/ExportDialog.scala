package com.lomekwi.cave.ui.topbar

import com.lomekwi.cave.util.Units.MEGA
import com.lomekwi.cave.util.i18n.I18N.i18n

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel
import com.kotcrab.vis.ui.widget.spinner.Spinner
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.resource.media.VdoRes
import com.lomekwi.cave.task.ExportOptions
import com.lomekwi.cave.task.ExportOptionsSet
import com.lomekwi.cave.task.ExportPresetsChangedEvent
import com.lomekwi.cave.task.VideoExportTask
import com.lomekwi.cave.ui.widget.FileChooserField
import com.lomekwi.cave.app.App

import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent

import scala.jdk.CollectionConverters.*

class ExportDialog(private val project: Project) extends VisDialog(i18n("导出视频")) {
  private var fileChooserField: FileChooserField = null
  private var widthModel: IntSpinnerModel = null
  private var heightModel: IntSpinnerModel = null
  private var fpsModel: SimpleFloatSpinnerModel = null
  private var bitrateModel: SimpleFloatSpinnerModel = null
  private var presetSet: ExportOptionsSet = null

  {
    presetSet = ExportOptionsSet.load()

    val content: Table = getContentTable

    val form: VisTable = new VisTable()

    fileChooserField = new FileChooserField(
      i18n("选择导出位置..."),
      NativeFileChooserIntent.SAVE
    )

    val pathRow: VisTable = new VisTable()
    pathRow.add(new VisLabel(i18n("输出文件"))).left().padRight(8)
    pathRow.add(fileChooserField).growX()
    form.add(pathRow).growX().pad(4).row()

    // ---- 分辨率 ----
    val dims: Array[Int] = detectDimensions()
    widthModel = new IntSpinnerModel(dims(0), 1, 7680, 1)
    heightModel = new IntSpinnerModel(dims(1), 1, 4320, 1)

    val dimRow: VisTable = new VisTable()
    dimRow.add(new VisLabel(i18n("宽度"))).left().padRight(8)
    dimRow.add(new Spinner("", widthModel)).width(100).padRight(16)
    dimRow.add(new VisLabel(i18n("高度"))).left().padRight(8)
    dimRow.add(new Spinner("", heightModel)).width(100)
    form.add(dimRow).growX().pad(4).row()

    // ---- 帧率 ----
    fpsModel = new SimpleFloatSpinnerModel(30f, 1, 120, 1, 1)

    val fpsRow: VisTable = new VisTable()
    fpsRow.add(new VisLabel(i18n("帧率"))).left().padRight(8)
    fpsRow.add(new Spinner("", fpsModel)).width(100)
    fpsRow.add(new VisLabel("fps")).padLeft(4)
    form.add(fpsRow).growX().pad(4).row()

    // ---- 码率 ----
    bitrateModel = new SimpleFloatSpinnerModel(6f, 0.1f, 100, 0.5f, 1)

    val bitrateRow: VisTable = new VisTable()
    bitrateRow.add(new VisLabel(i18n("码率"))).left().padRight(8)
    bitrateRow.add(new Spinner("", bitrateModel)).width(100)
    bitrateRow.add(new VisLabel("Mbps")).padLeft(4)
    form.add(bitrateRow).growX().pad(4).row()

    val scrollPane: VisScrollPane = new VisScrollPane(form)
    scrollPane.setFadeScrollBars(false)
    content.add(scrollPane).size(500, 280).row()

    // ---- 加载已保存的预设 ----
    if (presetSet.current().width > 0) {
      applyOptions(presetSet.current())
    }

    // ---- 导出按钮 ----
    addCloseButton()

    val exportBtn: VisTextButton = new VisTextButton(i18n("导出"))
    exportBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        startExport()
      }
    })

    val btnRow: VisTable = new VisTable()
    btnRow.add(exportBtn)
    content.add(btnRow).padTop(12).row()

    // ---- 预设 ----
    val presetRow: VisTable = new VisTable()
    val presetLabel: VisLabel = new VisLabel(presetLabelText())
    val prevBtn: VisTextButton = new VisTextButton("-")
    val saveBtn: VisTextButton = new VisTextButton(i18n("保存预设"))
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
        App.root.getToastManager().show(
          i18n("已保存预设 #") + (presetSet.currentIndex + 1), 1.5f)
      }
    })

    val delBtn: VisTextButton = new VisTextButton(i18n("删除"))
    delBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        if (presetSet.presets.size() <= 1) {
          App.root.getToastManager().show(i18n("至少保留一个预设"), 1.5f)
          return
        }
        presetSet.presets.remove(presetSet.currentIndex)
        if (presetSet.currentIndex >= presetSet.presets.size()) {
          presetSet.currentIndex = presetSet.presets.size() - 1
        }
        presetSet.save()
        presetLabel.setText(presetLabelText())
        applyOptions(presetSet.current())
        App.root.getToastManager().show(i18n("已删除预设"), 1.5f)
      }
    })

    presetRow.add(new VisLabel(i18n("预设"))).padRight(8)
    presetRow.add(prevBtn).width(40).padRight(4)
    presetRow.add(presetLabel).padRight(4)
    presetRow.add(nextBtn).width(40).padRight(12)
    presetRow.add(saveBtn)
    presetRow.add(delBtn).padLeft(4)
    content.add(presetRow).padTop(8).row()
  }

  private def detectDimensions(): Array[Int] = {
    for (res <- project.resources.values().asScala) {
      res match {
        case v: VdoRes => return Array(v.getWidth(), v.getHeight())
        case _ =>
      }
    }
    return Array(1920, 1080)
  }

  private def presetLabelText(): String = {
    s"${presetSet.currentIndex + 1}/${presetSet.presets.size()}"
  }

  private def collectOptions(): ExportOptions = {
    new ExportOptions(
      fileChooserField.getPath(),
      widthModel.getValue,
      heightModel.getValue,
      fpsModel.getValue,
      (bitrateModel.getValue * MEGA).toInt
    )
  }

  private def applyOptions(opts: ExportOptions): Unit = {
    if (opts.width == 0) {
      App.root.getToastManager().show(i18n("此预设为空，请先保存"), 2f)
      return
    }
    fileChooserField.setPath(opts.outputPath)
    widthModel.setValue(opts.width)
    heightModel.setValue(opts.height)
    fpsModel.setValue(opts.fps.toFloat)
    bitrateModel.setValue(opts.getBitrateMbps().toFloat)
  }

  private def startExport(): Unit = {
    val path: String = fileChooserField.getPath()
    if (path.isEmpty) {
      App.root.getToastManager().show(i18n("请选择输出文件"), 2f)
      return
    }

    val width: Int = widthModel.getValue
    val height: Int = heightModel.getValue
    val fps: Double = fpsModel.getValue.toDouble
    val bitrate: Int = (bitrateModel.getValue * MEGA).toInt

    val task = new VideoExportTask(
      project.timeline.duplicate(),
      new java.io.File(path),
      width, height, fps, bitrate
    )
    App.taskPool.submit(task)
    App.root.getToastManager().show(i18n("开始导出：") + new java.io.File(path).getName, 2f)
    fadeOut()
  }

  override def remove(): Boolean = {
    App.appEventBus.post(ExportPresetsChangedEvent)
    super.remove()
  }
}
