package com.lomekwi.cave.ui

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.List
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.util.ToastManager
import com.kotcrab.vis.ui.widget.LinkLabel
import com.kotcrab.vis.ui.widget.Menu
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.VisTextField
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.ui.editpanel.EditPanel
import com.lomekwi.cave.ui.editpanel.tlarea.TlGroup
import com.lomekwi.cave.ui.tabs.app.ProjectTab
import com.lomekwi.cave.ui.tabs.app.TopTabbedPane
import com.lomekwi.cave.ui.topbar.TopBar
import com.lomekwi.cave.app.App

import space.earlygrey.shapedrawer.ShapeDrawer

class Root extends ApplicationListener {
  private var stage: Stage = null

  private var toastManager: ToastManager = null

  private var mainLayout: VisTable = null
  private var majorArea: Container[Table] = null

  private var topBar: TopBar = null
  private var tabbedPane: TopTabbedPane = null

  private var dragAndDrop: DragAndDrop = null

  private var shapeDrawer: ShapeDrawer = null

  private var generator: FreeTypeFontGenerator = null

  App.root = this

  override def create(): Unit = {
    dragAndDrop = new DragAndDrop()

    val multiplexer: InputMultiplexer = new InputMultiplexer()

    multiplexer.addProcessor(0, new InputProcessor {
      override def keyDown(keycode: Int): Boolean = {
        // 无需项目的 TopActions
        if (App.shortcutManager.isActive(TopBar.TopActions.NEW)) {
          topBar.performNew()
          return true
        }
        if (App.shortcutManager.isActive(TopBar.TopActions.OPEN)) {
          topBar.performOpen()
          return true
        }
        if (App.shortcutManager.isActive(TopBar.TopActions.CLOSE)) {
          topBar.performClose()
          return true
        }

        val project: Project = getFrontendProject()
        if (project == null || project.undoManager == null) return false
        val ep: EditPanel = getFrontendEditPanel()
        // 需要项目的 TopActions
        if (App.shortcutManager.isActive(TopBar.TopActions.SAVE)) {
          topBar.performSave()
          return true
        }
        if (App.shortcutManager.isActive(TopBar.TopActions.SAVE_AS)) {
          topBar.performSaveAs()
          return true
        }

        if (isTextInputFocused()) return false

        // 复制（全局，无需项目）
        if (App.shortcutManager.isActive(TlGroup.Actions.COPY)) {
          App.copyManager.copy()
          return true
        }

        // 撤销 / 重做
        if (App.shortcutManager.isActive(TlGroup.Actions.UNDO)) {
          project.undoManager.undo()
          if (ep != null) ep.getTlGroup().markTimelineDirty()
          return true
        }
        if (App.shortcutManager.isActive(TlGroup.Actions.REDO)) {
          project.undoManager.redo()
          if (ep != null) ep.getTlGroup().markTimelineDirty()
          return true
        }

        return false
      }

      override def keyUp(keycode: Int): Boolean = { return false }

      override def keyTyped(character: Char): Boolean = { return false }

      override def touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = { return false }

      override def touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = { return false }

      override def touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = { return false }

      override def mouseMoved(screenX: Int, screenY: Int): Boolean = { return false }

      override def scrolled(amountX: Float, amountY: Float): Boolean = { return false }

      override def touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = { return false }
    })

    VisUI.load(injectChineseFont(VisUI.SkinScale.X2))
    stage = new Stage(new ScreenViewport())
    stage.addCaptureListener(new InputListener {
      override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
        if (button != Input.Buttons.LEFT) return false
        var target: Actor = event.getTarget
        while (target != null) {
          if (target.isInstanceOf[Focusable]) {
            val focusTarget: Actor = target
            Gdx.app.postRunnable(() => {
              stage.setKeyboardFocus(focusTarget)
              stage.setScrollFocus(focusTarget)
            })
            return false
          }
          target = target.getParent
        }
        return false
      }
    })
    multiplexer.addProcessor(stage)
    Gdx.input.setInputProcessor(multiplexer)

    toastManager = new ToastManager(stage)

    val white: Pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888)
    white.setColor(Color.WHITE)
    white.fill()
    val region: TextureRegion = new TextureRegion(new Texture(white))
    this.shapeDrawer = new ShapeDrawer(stage.getBatch, region)
    white.dispose()

    // 初始化UI组件
    mainLayout = new VisTable()
    mainLayout.setBackground("window-bg")
    mainLayout.setFillParent(true)

    topBar = new TopBar()
    mainLayout.top()
    mainLayout.add(topBar.getTable).fillX().top().row()

    tabbedPane = new TopTabbedPane()
    mainLayout.add(tabbedPane.getTable).fillX().top().row()
    tabbedPane.refreshVisibility()
    majorArea = new Container[Table] {
      override def getMinHeight(): Float = {
        0
      }
      override def getMinWidth(): Float = {
        0
      }
      override def getPrefHeight(): Float = {
        0
      }
      override def getPrefWidth(): Float = {
        0
      }
    }
    majorArea.fill()
    mainLayout.add(majorArea).fill().expand().row()

    stage.addActor(mainLayout)

    registerDefaultShortcuts()

    App.appEventBus.register(App.copyManager)

    toastManager.toFront()

    stage.setDebugAll(false)
  }

  override def render(): Unit = {
    ScreenUtils.clear(Color.BLACK)
    stage.act(Math.min(Gdx.graphics.getDeltaTime, 1 / 30f))
    stage.draw()
  }

  override def pause(): Unit = {}

  override def resume(): Unit = {}

  override def resize(width: Int, height: Int): Unit = {
    if (stage != null) {
      stage.getViewport.update(width, height, true)
      toastManager.resize()
    }
  }

  override def dispose(): Unit = {
    if (stage != null) stage.dispose()
    VisUI.dispose()
    generator.dispose()
  }
  def getStage(): Stage = {
    stage
  }

  def isTextInputFocused(): Boolean = {
    if (stage == null) return false
    val focus = stage.getKeyboardFocus
    return focus.isInstanceOf[TextField] || focus.isInstanceOf[VisTextField]
  }

  def getMainLayout(): VisTable = {
    mainLayout
  }
  def getMajorArea(): Container[Table] = {
    majorArea
  }
  def getTabbedPane(): TopTabbedPane = {
    tabbedPane
  }
  private def injectChineseFont(scale: VisUI.SkinScale): Skin = {
    val skin: Skin = new Skin(scale.getSkinFile)

    generator = new FreeTypeFontGenerator(Gdx.files.internal("font/noto.otf"))

    val param: FreeTypeFontGenerator.FreeTypeFontParameter =
      new FreeTypeFontGenerator.FreeTypeFontParameter()

    param.size = 22
    param.incremental = true

    val font: BitmapFont = generator.generateFont(param)

    skin.add("default-font", font)

    skin.get("default", classOf[Label.LabelStyle]).font = font
    skin.get("default", classOf[TextButton.TextButtonStyle]).font = font
    skin.get("default", classOf[TextField.TextFieldStyle]).font = font
    skin.get("default", classOf[CheckBox.CheckBoxStyle]).font = font
    skin.get("default", classOf[MenuItem.MenuItemStyle]).font = font
    skin.get("default", classOf[Menu.MenuStyle]).openButtonStyle.font = font
    skin.get("default", classOf[TabbedPane.TabbedPaneStyle]).buttonStyle.font = font
    skin.get("default", classOf[Window.WindowStyle]).titleFont = font
    skin.get("default", classOf[LinkLabel.LinkLabelStyle]).font = font
    skin.get("default", classOf[VisTextButton.VisTextButtonStyle]).font = font
    skin.get("default", classOf[VisTextField.VisTextFieldStyle]).font = font
    val selectStyle: SelectBox.SelectBoxStyle = skin.get("default", classOf[SelectBox.SelectBoxStyle])
    selectStyle.font = font
    selectStyle.listStyle.font = font
    skin.get("default", classOf[List.ListStyle]).font = font
    skin
  }

  def getFrontendProject(): Project = {
    if (tabbedPane.getActiveTab.isInstanceOf[ProjectTab]) {
      return tabbedPane.getActiveTab.asInstanceOf[ProjectTab].getProject()
    } else {
      return null
    }
  }

  def getFrontendEditPanel(): EditPanel = {
    if (tabbedPane.getActiveTab.isInstanceOf[ProjectTab]) {
      return tabbedPane.getActiveTab.asInstanceOf[ProjectTab].getEditPanel()
    } else {
      return null
    }
  }

  private def registerDefaultShortcuts(): Unit = {
    for (action <- TlGroup.Actions.values) {
      App.shortcutManager.register(action, action.defaultKeys()*)
    }
    for (action <- TopBar.TopActions.values) {
      App.shortcutManager.register(action, action.defaultKeys()*)
    }
    App.shortcutManager.load()
    topBar.applyCustomShortcuts()
  }

  def getDragAndDrop(): DragAndDrop = {
    dragAndDrop
  }

  def getShapeDrawer(): ShapeDrawer = {
    shapeDrawer
  }

  def getToastManager(): ToastManager = {
    toastManager
  }
}
