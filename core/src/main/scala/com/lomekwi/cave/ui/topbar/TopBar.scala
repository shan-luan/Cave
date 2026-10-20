package com.lomekwi.cave.ui.topbar

import TopBar.*

import com.lomekwi.cave.app.App.fileChooser
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.google.common.eventbus.Subscribe
import com.kotcrab.vis.ui.widget.LinkLabel
import com.kotcrab.vis.ui.widget.Menu
import com.kotcrab.vis.ui.widget.MenuBar
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisProgressBar
import com.kotcrab.vis.ui.widget.VisTable
import com.lomekwi.cave.app.shortcut.ShortcutAction
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.project.ProjectLoadedEvent
import com.lomekwi.cave.project.Project.hasProjectExtension
import com.lomekwi.cave.task.Task
import com.lomekwi.cave.ui.editpanel.tlarea.TimelineView
import com.lomekwi.cave.ui.listeners.ChangeListenerX
import com.lomekwi.cave.ui.settings.SettingsDialog
import com.lomekwi.cave.ui.tabs.app.TabSwitchedEvent
import com.lomekwi.cave.app.App

import java.io.File
import java.io.IOException
import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent

import com.badlogic.gdx.Input.Keys.*

import scala.collection.mutable

class TopBar extends MenuBar {
  private final val toastTimeOut: Float = 2f
  private final val actionItems: mutable.LinkedHashMap[TopActions, MenuItem] = mutable.LinkedHashMap.empty[TopActions, MenuItem]

  {
    val fileMenu: Menu = new MenuX(("文件".in(Zh) | "File".in(En)).str())

    val newItem: MenuItem = new MenuItem(("新建".in(Zh) | "New".in(En)).str(), new ChangeListenerX(() => performNew()))
    newItem.setShortcut(TopActions.NEW.defaultKeys()*)
    actionItems.put(TopActions.NEW, newItem)
    fileMenu.addItem(newItem)

    val openItem: MenuItem = new MenuItem(("打开".in(Zh) | "Open".in(En)).str(), new ChangeListenerX(() => performOpen()))
    openItem.setShortcut(TopActions.OPEN.defaultKeys()*)
    actionItems.put(TopActions.OPEN, openItem)
    fileMenu.addItem(openItem)

    fileMenu.addSeparator()

    val saveItem: MenuItem = new MenuItemP(("保存".in(Zh) | "Save".in(En)).str(), new ChangeListenerX(() => performSave()))
    saveItem.setShortcut(TopActions.SAVE.defaultKeys()*)
    actionItems.put(TopActions.SAVE, saveItem)
    fileMenu.addItem(saveItem)

    val saveAsItem: MenuItem = new MenuItemP(("另存为".in(Zh) | "Save As".in(En)).str(), new ChangeListenerX(() => performSaveAs()))
    saveAsItem.setShortcut(TopActions.SAVE_AS.defaultKeys()*)
    actionItems.put(TopActions.SAVE_AS, saveAsItem)
    fileMenu.addItem(saveAsItem)

    fileMenu.addSeparator()

    fileMenu.addItem(new MenuItemP(("导出".in(Zh) | "Export".in(En)).str(), new ChangeListenerX(() => {
      val context = App.root.getFrontendContext
      if (context != null) {
        App.root.showDialog(new ExportDialog(context))
      }
    })))

    fileMenu.addSeparator()

    val closeItem: MenuItem = new MenuItem(("关闭".in(Zh) | "Close".in(En)).str(), new ChangeListenerX(() => performClose()))
    closeItem.setShortcut(TopActions.CLOSE.defaultKeys()*)
    actionItems.put(TopActions.CLOSE, closeItem)
    fileMenu.addItem(closeItem)

    addMenu(fileMenu)

    val editMenu: Menu = new MenuX(("编辑".in(Zh) | "Edit".in(En)).str())

    val undoItem: MenuItem = new MenuItemP(("撤销".in(Zh) | "Undo".in(En)).str(), new ChangeListenerX(() => {
      val context = App.root.getFrontendContext
      if (context != null) {
        context.undo.undo()
        val ep = App.root.getFrontendEditPanel
        if (ep != null) ep.getTimelineView.markTimelineDirty()
      }
    }))
    undoItem.setShortcut(TimelineView.Actions.UNDO.defaultKeys()*)
    editMenu.addItem(undoItem)

    val redoItem: MenuItem = new MenuItemP(("重做".in(Zh) | "Redo".in(En)).str(), new ChangeListenerX(() => {
      val context = App.root.getFrontendContext
      if (context != null) {
        context.undo.redo()
        val ep = App.root.getFrontendEditPanel
        if (ep != null) ep.getTimelineView.markTimelineDirty()
      }
    }))
    redoItem.setShortcut(TimelineView.Actions.REDO.defaultKeys()*)
    editMenu.addItem(redoItem)

    addMenu(editMenu)

    addMenu(new MenuX(("工具".in(Zh) | "Tools".in(En)).str())
      .withItem(new MenuItem(("设置".in(Zh) | "Settings".in(En)).str(), new ChangeListenerX(() => {
        new SettingsDialog()
      })))
      .withItem(new MenuItem(("后台任务".in(Zh) | "Background Tasks".in(En)).str(), new ChangeListenerX(() => {

        val taskWin: VisDialog = new VisDialog(("后台任务".in(Zh) | "Background Tasks".in(En)).str()) {
          final val rows: mutable.HashMap[Task, VisTable] = mutable.HashMap.empty[Task, VisTable]
          final val current: mutable.HashSet[Task] = mutable.HashSet.empty[Task]
          var dirty: Boolean = false

          override def act(delta: Float): Unit = {
            super.act(delta)
            val content: Table = getContentTable

            current.clear()
            for (task <- App.taskPool) {
              current += task
              var row: VisTable = rows.getOrElse(task, null)
              if (row == null) {
                dirty = true
                row = new VisTable()
                val bar: VisProgressBar = new VisProgressBar(0, 1, 0.01f, false)
                row.add(new VisLabel(task.getName)).left()
                row.add(bar).growX()
                row.setUserObject(bar)
                rows.put(task, row)
                content.add(row).growX()
                content.row()
              }
              row.getUserObject.asInstanceOf[VisProgressBar].setValue(task.getProgress)
            }

            for (task <- rows.keys.filterNot(current.contains).toList) {
              dirty = true
              content.removeActor(rows(task))
              rows.remove(task)
            }
            if (dirty) {
              content.pack()
              pack()
              dirty = false
            }
          }
        }
        taskWin.addCloseButton()
        App.root.showDialog(taskWin)
      })))
    )

    addMenu(new MenuX(("视图".in(Zh) | "View".in(En)).str())
      .withItem(new MenuItemP(("复位".in(Zh) | "Reset View".in(En)).str(), new ChangeListenerX(() => {
        val editPanel = App.root.getFrontendEditPanel
        if (editPanel != null) {
          editPanel.previewArea.resetView()
        }
      })))
    )

    addMenu(new MenuX(("帮助".in(Zh) | "Help".in(En)).str())
      .withItem(new MenuItem(("关于".in(Zh) | "About".in(En)).str(), new ChangeListenerX(() => {
        val about: VisDialog = new VisDialog(("关于".in(Zh) | "About".in(En)).str())
        about.addCloseButton()
        val ct = about.getContentTable
        ct.add(new Label(("CAVE:Cave is Another Video Editor是自由的多媒体编辑软件".in(Zh) | "CAVE: Cave is Another Video Editor is free multimedia editing software".in(En)).str(), about.getSkin)).left()
        ct.row()
        ct.add(new Label(("由shan_luan_开发.此软件以AGPLv3分发并不提供任何保修.".in(Zh) | "Developed by shan_luan_. This software is distributed under AGPLv3 without any warranty.".in(En)).str(), about.getSkin)).left()
        ct.row()
        ct.add(new LinkLabel("Github", "https://github.com/shan-luan/Cave")).left()
        ct.row()
        ct.add(new LinkLabel(("B站".in(Zh) | "Bilibili".in(En)).str(), "https://space.bilibili.com/1655518235")).left()
        ct.row()
        App.root.showDialog(about)
      }))
    ))
  }
  def applyCustomShortcuts(): Unit = {
    for ((action, item) <- actionItems) {
      val keys = App.shortcutManager.getKeys(action)
      val arr: Array[Int] = keys.toArray
      item.setShortcut(arr*)
    }
  }

  // 全局快捷键动作，由 [[com.lomekwi.cave.ui.Root]] 中的全局 InputProcessor 调用

  def performNew(): Unit = {
    try {
      App.appEventBus.post(ProjectLoadedEvent(Project.create()))
      App.root.toastManager.show(("项目已新建".in(Zh) | "Project created".in(En)).str(), toastTimeOut)
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }

  def performOpen(): Unit = {
    try {
      val conf: NativeFileChooserConfiguration = new NativeFileChooserConfiguration()
      conf.title = ("选择项目...".in(Zh) | "Choose Project...".in(En)).str()
      if (Gdx.app.getType == Application.ApplicationType.Android) {
        conf.mimeFilter = "*/*"
      } else {
        conf.nameFilter = (dir: File, name: String) => hasProjectExtension(name)
      }
      conf.intent = NativeFileChooserIntent.OPEN
      fileChooser.chooseFile(conf, new NativeFileChooserCallback {
        override def onFileChosen(file: FileHandle): Unit = {
          try {
            if (!hasProjectExtension(file.name())) {
              App.root.toastManager.show(("请选择 .cave 项目文件".in(Zh) | "Please choose a .cave project file".in(En)).str(), toastTimeOut)
            } else {
              App.appEventBus.post(ProjectLoadedEvent(Project.open(file)))
              App.root.toastManager.show(("项目已打开".in(Zh) | "Project opened".in(En)).str(), toastTimeOut)
            }
          } catch {
            case e @ (_: IOException | _: ClassNotFoundException) =>
              e.printStackTrace()
          }
        }
        override def onCancellation(): Unit = {}
        override def onError(exception: Exception): Unit = {}
      })
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }

  def performSave(): Unit = {
    try {
      val context = App.root.getFrontendContext
      if (context != null) {
        if (context.savePath.isEmpty) {
          val conf: NativeFileChooserConfiguration = new NativeFileChooserConfiguration()
          conf.title = ("选择保存位置...".in(Zh) | "Choose Save Location...".in(En)).str()
          if (Gdx.app.getType == Application.ApplicationType.Android) {
            conf.mimeFilter = "*/*"
          }
          conf.intent = NativeFileChooserIntent.SAVE
          fileChooser.chooseFile(conf, new NativeFileChooserCallback {
            override def onFileChosen(file: FileHandle): Unit = {
              try {
                val c = App.root.getFrontendContext
                if (c != null) {
                  Project.save(c, file)
                  App.root.toastManager.show(("项目已保存".in(Zh) | "Project saved".in(En)).str(), toastTimeOut)
                }
              } catch {
                case e: IOException =>
                  e.printStackTrace()
              }
            }
            override def onCancellation(): Unit = {}
            override def onError(exception: Exception): Unit = {}
          })
        } else {
          Project.save(context)
          App.root.toastManager.show(("项目已保存".in(Zh) | "Project saved".in(En)).str(), toastTimeOut)
        }
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }

  def performSaveAs(): Unit = {
    try {
      if (App.root.getFrontendContext != null) {
        val conf: NativeFileChooserConfiguration = new NativeFileChooserConfiguration()
        conf.title = ("选择保存位置...".in(Zh) | "Choose Save Location...".in(En)).str()
        if (Gdx.app.getType == Application.ApplicationType.Android) {
          conf.mimeFilter = "*/*"
        }
        conf.intent = NativeFileChooserIntent.SAVE
        fileChooser.chooseFile(conf, new NativeFileChooserCallback {
          override def onFileChosen(file: FileHandle): Unit = {
            try {
              val c = App.root.getFrontendContext
              if (c != null) {
                Project.save(c, file)
                App.root.toastManager.show(("项目已保存".in(Zh) | "Project saved".in(En)).str(), toastTimeOut)
              }
            } catch {
              case e: IOException =>
                e.printStackTrace()
            }
          }
          override def onCancellation(): Unit = {}
          override def onError(exception: Exception): Unit = {}
        })
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }

  def performClose(): Unit = {
    try {
      Gdx.app.exit()
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }
}

object TopBar {
  enum TopActions(displayName0: String, defaultKeys0: Int*) extends ShortcutAction {
    case NEW extends TopActions(("新建文件".in(Zh) | "New File".in(En)).str(), CONTROL_LEFT, N)
    case OPEN extends TopActions(("打开文件".in(Zh) | "Open File".in(En)).str(), CONTROL_LEFT, O)
    case SAVE extends TopActions(("保存".in(Zh) | "Save".in(En)).str(), CONTROL_LEFT, S)
    case SAVE_AS extends TopActions(("另存为".in(Zh) | "Save As".in(En)).str(), CONTROL_LEFT, SHIFT_LEFT, S)
    case CLOSE extends TopActions(("关闭窗口".in(Zh) | "Close Window".in(En)).str(), CONTROL_LEFT, W)

    override def displayName(): String = displayName0

    override def defaultKeys(): Array[Int] = defaultKeys0.toArray
  }

  class MenuX(title: String) extends Menu(title) {
    def withItem(item: MenuItem): MenuX = {
      super.addItem(item)
      this
    }

    def withSeparator(): MenuX = {
      super.addSeparator()
      this
    }
  }

  /**
   * 与项目关联的菜单项，没有可用项目时自动禁用
   */
  private class MenuItemP(text: String) extends MenuItem(text) {
    App.appEventBus.register(this)
    setDisabled(true)

    def this(text: String, changeListener: ChangeListener) = {
      this(text)
      addListener(changeListener)
    }

    @Subscribe
    def onTabSwitched(event: TabSwitchedEvent): Unit = {
      setDisabled(App.root.getFrontendProject == null)
    }
  }
}
