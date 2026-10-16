package com.lomekwi.cave.ui.tabs.app

import com.lomekwi.cave.util.i18n.I18N.i18n

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.google.common.eventbus.Subscribe
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import com.lomekwi.cave.app.App
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.project.ProjectSession
import com.lomekwi.cave.project.ProjectDirtyChangedEvent
import com.lomekwi.cave.ui.editpanel.EditPanel
import com.lomekwi.cave.ui.editpanel.EditPanelFrame
import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent

import java.io.IOException

class ProjectTab(project0: Project) extends Tab(true, true) {
  final val context: ProjectSession = new ProjectSession(project0)
  final val editPanel: EditPanel = new EditPanel(context)
  context.bus.register(this)

  def project: Project = context.project

  override def getTabTitle: String = {
    context.project.name
  }
  override def getContentTable: Table = {
    EditPanelFrame.getINSTANCE.`with`(editPanel)
  }

  @Subscribe
  def onProjectDirtyChanged(event: ProjectDirtyChangedEvent): Unit = {
    setDirty(context.isDirty)
  }

  //FIXME 当保存时，如果取消文件选择器，则会在未保存的情况下关闭标签页。这是vis-ui的设计缺陷且我已经打开一个issue(#405)
  override def save(): Boolean = {
    if (context.savePath.isEmpty) {
      val conf = new NativeFileChooserConfiguration()
      conf.title = i18n("选择保存位置...")
      if (Gdx.app.getType == Application.ApplicationType.Android) {
        conf.mimeFilter = "*/*"
      }
      conf.intent = NativeFileChooserIntent.SAVE
      App.fileChooser.chooseFile(conf, new NativeFileChooserCallback {
        override def onFileChosen(file: com.badlogic.gdx.files.FileHandle): Unit = {
          try {
            Project.save(context, file)
            setDirty(false)
          } catch {
            case e: IOException =>
              Gdx.app.error("ProjectTab", "保存项目失败", e)
          }
        }
        override def onCancellation(): Unit = {}
        override def onError(exception: Exception): Unit = {
          Gdx.app.error("ProjectTab", "保存项目失败", exception)
        }
      })
      true
    }
    try {
      Project.save(context)
      setDirty(false)
      true
    } catch {
      case e: IOException =>
        Gdx.app.error("ProjectTab", "保存项目失败", e)
        false
    }
  }

  //不需要手动调用！
  override def dispose(): Unit = {
    context.bus.unregister(this)
    super.dispose()
    context.close()
    editPanel.dispose()
  }
}
