package com.lomekwi.cave.ui.tabs.app

import com.lomekwi.cave.util.i18n.I18N.i18n

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.google.common.eventbus.Subscribe
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import com.lomekwi.cave.app.App
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.project.ProjectDirtyChangedEvent
import com.lomekwi.cave.project.Projects
import com.lomekwi.cave.ui.editpanel.EditPanel
import com.lomekwi.cave.ui.editpanel.EditPanelFrame
import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent

import java.io.IOException

class ProjectTab(project0: Project) extends Tab(true, true) {
  private final val editPanel: EditPanel = new EditPanel(project0)
  private final val project: Project = project0
  project.projEventBus.register(this)

  override def getTabTitle: String = {
    project.name
  }
  override def getContentTable: Table = {
    EditPanelFrame.getINSTANCE.`with`(editPanel)
  }

  def getEditPanel: EditPanel = {
    editPanel
  }

  def getProject: Project = {
    project
  }

  @Subscribe
  def onProjectDirtyChanged(event: ProjectDirtyChangedEvent): Unit = {
    setDirty(project.isDirty)
  }

  //FIXME：当保存时，如果取消文件选择器，则会在未保存的情况下关闭标签页。这是vis-ui的设计缺陷且我已经打开一个issue(#405)
  override def save(): Boolean = {
    if (project.getSavePath == null) {
      val conf = new NativeFileChooserConfiguration()
      conf.title = i18n("选择保存位置...")
      if (Gdx.app.getType == Application.ApplicationType.Android) {
        conf.mimeFilter = "*/*"
      }
      conf.intent = NativeFileChooserIntent.SAVE
      App.fileChooser.chooseFile(conf, new NativeFileChooserCallback {
        override def onFileChosen(file: com.badlogic.gdx.files.FileHandle): Unit = {
          try {
            Projects.save(project, file)
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
      Projects.save(project)
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
    project.projEventBus.unregister(this)
    super.dispose()
    project.close()
    editPanel.dispose()
  }
}
