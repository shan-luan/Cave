package com.lomekwi.cave.ui.widget

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.Gdx
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.VisValidatableTextField
import com.lomekwi.cave.app.App

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent

/**
 * 一个带文本字段和浏览按钮的文件选择器小组件。
 * 点击浏览按钮会弹出系统原生文件选择对话框，选中后路径自动填入文本框。
 */
class FileChooserField(private val chooserTitle: String, private val intent: NativeFileChooserIntent) extends VisTable {
  private final val pathField: VisValidatableTextField = new VisValidatableTextField("")
  private final val browseBtn: VisTextButton = new VisTextButton("浏览")

  {
    browseBtn.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        openChooser()
      }
    })

    add(pathField).growX().padRight(8)
    add(browseBtn)
  }

  private def openChooser(): Unit = {
    val conf = new NativeFileChooserConfiguration()
    conf.title = chooserTitle
    conf.intent = intent
    conf.nameFilter = (dir, name) => true
    App.fileChooser.chooseFile(conf, new NativeFileChooserCallback {
      override def onFileChosen(file: FileHandle): Unit = {
        pathField.setText(file.file().getAbsolutePath)
      }

      override def onCancellation(): Unit = {}

      override def onError(exception: Exception): Unit = {
        Gdx.app.error("FileChooserField", "选择文件失败", exception)
      }
    })
  }

  /** 获取当前路径文本（已 trim）。 */
  def getPath(): String = {
    pathField.getText.trim()
  }

  /** 设置路径文本。 */
  def setPath(path: String): Unit = {
    pathField.setText(path)
  }

  /** 暴露底层文本框，以便进行更细粒度的控制（如添加验证器）。 */
  def getPathField(): VisValidatableTextField = {
    pathField
  }
}
