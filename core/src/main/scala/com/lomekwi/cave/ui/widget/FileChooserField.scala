package com.lomekwi.cave.ui.widget

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.Gdx
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.VisValidatableTextField
import com.lomekwi.cave.app.App
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent

/**
 * 一个带文本字段和浏览按钮的文件选择器小组件。
 */
class FileChooserField(private val chooserTitle: String, private val intent: NativeFileChooserIntent) extends VisTable {
  private final val pathField: VisValidatableTextField = new VisValidatableTextField("")
  private final val browseBtn: VisTextButton = new VisTextButton(("浏览".in(Zh) | "Browse".in(En)).str())

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
        Gdx.app.error("FileChooserField", ("选择文件失败".in(Zh) | "Failed to choose file".in(En)).str(), exception)
      }
    })
  }

  /** 获取当前路径文本。 */
  def getPath: String = {
    pathField.getText.trim()
  }

  def setPath(path: String): Unit = {
    pathField.setText(path)
  }

}
