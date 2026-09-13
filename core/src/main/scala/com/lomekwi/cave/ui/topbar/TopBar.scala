package com.lomekwi.cave.ui.topbar

import TopBar.*

import com.lomekwi.cave.app.App.fileChooser
import com.lomekwi.cave.util.i18n.I18N.i18n

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
import com.lomekwi.cave.project.ProjectLoadedEvent
import com.lomekwi.cave.project.Projects
import com.lomekwi.cave.project.Projects.hasProjectExtension
import com.lomekwi.cave.task.Task
import com.lomekwi.cave.ui.editpanel.tlarea.TlGroup
import com.lomekwi.cave.ui.listeners.ChangeListenerX
import com.lomekwi.cave.ui.settings.SettingsDialog
import com.lomekwi.cave.ui.tabs.app.TabSwitchedEvent
import com.lomekwi.cave.app.App

import java.io.File
import java.io.IOException
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Map

import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent

import com.badlogic.gdx.Input.Keys.*

import scala.jdk.CollectionConverters.*

class TopBar extends MenuBar {
  private final val toastTimeOut: Float = 2f
  private final val actionItems: Map[TopActions, MenuItem] = new LinkedHashMap[TopActions, MenuItem]()

  {
    val fileMenu: Menu = new MenuX(i18n("文件"))

    val newItem: MenuItem = new MenuItem(i18n("新建"), new ChangeListenerX(() => performNew()))
    newItem.setShortcut(TopActions.NEW.defaultKeys()*)
    actionItems.put(TopActions.NEW, newItem)
    fileMenu.addItem(newItem)

    val openItem: MenuItem = new MenuItem(i18n("打开"), new ChangeListenerX(() => performOpen()))
    openItem.setShortcut(TopActions.OPEN.defaultKeys()*)
    actionItems.put(TopActions.OPEN, openItem)
    fileMenu.addItem(openItem)

    fileMenu.addSeparator()

    val saveItem: MenuItem = new MenuItemP(i18n("保存"), new ChangeListenerX(() => performSave()))
    saveItem.setShortcut(TopActions.SAVE.defaultKeys()*)
    actionItems.put(TopActions.SAVE, saveItem)
    fileMenu.addItem(saveItem)

    val saveAsItem: MenuItem = new MenuItemP(i18n("另存为"), new ChangeListenerX(() => performSaveAs()))
    saveAsItem.setShortcut(TopActions.SAVE_AS.defaultKeys()*)
    actionItems.put(TopActions.SAVE_AS, saveAsItem)
    fileMenu.addItem(saveAsItem)

    fileMenu.addSeparator()

    fileMenu.addItem(new MenuItemP(i18n("导出"), new ChangeListenerX(() => {
      val project = App.root.getFrontendProject()
      if (project != null) {
        new ExportDialog(project).show(App.root.getStage())
      }
    })))

    fileMenu.addSeparator()

    val closeItem: MenuItem = new MenuItem(i18n("关闭"), new ChangeListenerX(() => performClose()))
    closeItem.setShortcut(TopActions.CLOSE.defaultKeys()*)
    actionItems.put(TopActions.CLOSE, closeItem)
    fileMenu.addItem(closeItem)

    addMenu(fileMenu)

    val editMenu: Menu = new MenuX(i18n("编辑"))

    val undoItem: MenuItem = new MenuItemP(i18n("撤销"), new ChangeListenerX(() => {
      val project = App.root.getFrontendProject()
      if (project != null) {
        project.undoManager.undo()
        val ep = App.root.getFrontendEditPanel()
        if (ep != null) ep.getTlGroup().markTimelineDirty()
      }
    }))
    undoItem.setShortcut(TlGroup.Actions.UNDO.defaultKeys()*)
    editMenu.addItem(undoItem)

    val redoItem: MenuItem = new MenuItemP(i18n("重做"), new ChangeListenerX(() => {
      val project = App.root.getFrontendProject()
      if (project != null) {
        project.undoManager.redo()
        val ep = App.root.getFrontendEditPanel()
        if (ep != null) ep.getTlGroup().markTimelineDirty()
      }
    }))
    redoItem.setShortcut(TlGroup.Actions.REDO.defaultKeys()*)
    editMenu.addItem(redoItem)

    addMenu(editMenu)

    addMenu(new MenuX(i18n("工具"))
      .withItem(new MenuItem(i18n("设置"), new ChangeListenerX(() => {
        new SettingsDialog()
      })))
      .withItem(new MenuItem(i18n("后台任务"), new ChangeListenerX(() => {

        val taskWin: VisDialog = new VisDialog(i18n("后台任务")) {
          final val rows: HashMap[Task, VisTable] = new HashMap[Task, VisTable]()
          final val current: HashSet[Task] = new HashSet[Task]()
          var dirty: Boolean = false

          override def act(delta: Float): Unit = {
            super.act(delta)
            val content: Table = getContentTable()

            current.clear()
            for (task <- App.taskPool.asScala) {
              current.add(task)
              var row: VisTable = rows.get(task)
              if (row == null) {
                dirty = true
                row = new VisTable()
                val bar: VisProgressBar = new VisProgressBar(0, 1, 0.01f, false)
                row.add(new VisLabel(task.getName())).left()
                row.add(bar).growX()
                row.setUserObject(bar)
                rows.put(task, row)
                content.add(row).growX()
                content.row()
              }
              row.getUserObject().asInstanceOf[VisProgressBar].setValue(task.getProgress())
            }

            rows.entrySet().removeIf((entry: Map.Entry[Task, VisTable]) => {
              if (!current.contains(entry.getKey())) {
                dirty = true
                content.removeActor(entry.getValue())
                true
              } else {
                false
              }
            })
            if (dirty) {
              content.pack()
              pack()
              dirty = false
            }
          }
        }
        taskWin.addCloseButton()
        taskWin.show(App.root.getStage())
      })))
    )

    addMenu(new MenuX(i18n("视图"))
      .withItem(new MenuItemP(i18n("复位"), new ChangeListenerX(() => {
        val editPanel = App.root.getFrontendEditPanel()
        if (editPanel != null) {
          editPanel.getPreviewArea().resetView()
        }
      })))
    )

    addMenu(new MenuX(i18n("帮助"))
      .withItem(new MenuItem(i18n("关于"), new ChangeListenerX(() => {
        val about: VisDialog = new VisDialog(i18n("关于"))
        about.addCloseButton()
        val ct = about.getContentTable()
        ct.add(new Label(i18n("CAVE:Cave is Another Video Editor是自由的多媒体编辑软件"), about.getSkin())).left()
        ct.row()
        ct.add(new Label(i18n("由shan_luan_开发.此软件以AGPLv3分发并不提供任何保修."), about.getSkin())).left()
        ct.row()
        ct.add(new LinkLabel("Github", "https://github.com/shan-luan/Cave")).left()
        ct.row()
        ct.add(new LinkLabel(i18n("B站"), "https://space.bilibili.com/1655518235")).left()
        ct.row()
        about.show(App.root.getStage())
      }))
    ))
  }
  def applyCustomShortcuts(): Unit = {
    for (e <- actionItems.entrySet().asScala) {
      val keys = App.shortcutManager.getKeys(e.getKey())
      val arr: Array[Int] = keys.stream().mapToInt((i: Integer) => i.intValue()).toArray()
      e.getValue().setShortcut(arr*)
    }
  }

  // -- 全局快捷键动作（由 Root.InputProcessor 调用） --

  def performNew(): Unit = {
    try {
      App.appEventBus.post(new ProjectLoadedEvent(Projects.create()))
      App.root.getToastManager().show(i18n("项目已新建"), toastTimeOut)
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }

  def performOpen(): Unit = {
    try {
      val conf: NativeFileChooserConfiguration = new NativeFileChooserConfiguration()
      conf.title = i18n("选择项目...")
      if (Gdx.app.getType() == Application.ApplicationType.Android) {
        conf.mimeFilter = "*/*"
      } else {
        conf.nameFilter = (dir: File, name: String) => hasProjectExtension(name)
      }
      conf.intent = NativeFileChooserIntent.OPEN
      fileChooser.chooseFile(conf, new NativeFileChooserCallback {
        override def onFileChosen(file: FileHandle): Unit = {
          try {
            if (!hasProjectExtension(file.name())) {
              App.root.getToastManager().show(i18n("请选择 .cave 项目文件"), toastTimeOut)
              return
            }
            App.appEventBus.post(new ProjectLoadedEvent(Projects.open(file)))
            App.root.getToastManager().show(i18n("项目已打开"), toastTimeOut)
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
      if (App.root.getFrontendProject() == null) return

      if (App.root.getFrontendProject().getSavePath() == null) {
        val conf: NativeFileChooserConfiguration = new NativeFileChooserConfiguration()
        conf.title = i18n("选择保存位置...")
        if (Gdx.app.getType() == Application.ApplicationType.Android) {
          conf.mimeFilter = "*/*"
        }
        conf.intent = NativeFileChooserIntent.SAVE
        fileChooser.chooseFile(conf, new NativeFileChooserCallback {
          override def onFileChosen(file: FileHandle): Unit = {
            try {
              if (App.root.getFrontendProject() != null) {
                Projects.save(App.root.getFrontendProject(), file)
                App.root.getToastManager().show(i18n("项目已保存"), toastTimeOut)
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
        Projects.save(App.root.getFrontendProject())
        App.root.getToastManager().show(i18n("项目已保存"), toastTimeOut)
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }

  def performSaveAs(): Unit = {
    try {
      if (App.root.getFrontendProject() == null) return

      val conf: NativeFileChooserConfiguration = new NativeFileChooserConfiguration()
      conf.title = i18n("选择保存位置...")
      if (Gdx.app.getType() == Application.ApplicationType.Android) {
        conf.mimeFilter = "*/*"
      }
      conf.intent = NativeFileChooserIntent.SAVE
      fileChooser.chooseFile(conf, new NativeFileChooserCallback {
        override def onFileChosen(file: FileHandle): Unit = {
          try {
            if (App.root.getFrontendProject() != null) {
              Projects.save(App.root.getFrontendProject(), file)
              App.root.getToastManager().show(i18n("项目已保存"), toastTimeOut)
            }
          } catch {
            case e: IOException =>
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
    case NEW extends TopActions("新建文件", CONTROL_LEFT, N)
    case OPEN extends TopActions("打开文件", CONTROL_LEFT, O)
    case SAVE extends TopActions("保存", CONTROL_LEFT, S)
    case SAVE_AS extends TopActions("另存为", CONTROL_LEFT, SHIFT_LEFT, S)
    case CLOSE extends TopActions("关闭窗口", CONTROL_LEFT, W)

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
   * 与项目关联的菜单项，当当前没有可用项目时会自动禁用
   */
  class MenuItemP(text: String) extends MenuItem(text) {
    App.appEventBus.register(this)
    setDisabled(true)

    def this(text: String, changeListener: ChangeListener) = {
      this(text)
      addListener(changeListener)
    }

    @Subscribe
    def onTabSwitched(event: TabSwitchedEvent): Unit = {
      setDisabled(App.root.getFrontendProject() == null)
    }
  }
}
