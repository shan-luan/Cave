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
import com.lomekwi.cave.ui.editpanel.tlarea.TimelineView
import com.lomekwi.cave.ui.tabs.app.ProjectTab
import com.lomekwi.cave.ui.tabs.app.TopTabbedPane
import com.lomekwi.cave.ui.topbar.TopBar
import com.lomekwi.cave.app.App

import space.earlygrey.shapedrawer.ShapeDrawer
import scala.compiletime.uninitialized

class Root extends ApplicationListener {
  private var stage: Stage = uninitialized

  private var toastManager: ToastManager = uninitialized

  private var mainLayout: VisTable = uninitialized
  private var majorArea: Container[Table] = uninitialized

  private var topBar: TopBar = uninitialized
  private var tabbedPane: TopTabbedPane = uninitialized

  private var dragAndDrop: DragAndDrop = uninitialized

  private var shapeDrawer: ShapeDrawer = uninitialized

  private var generator: FreeTypeFontGenerator = uninitialized

  App.root = this

  override def create(): Unit = {
    dragAndDrop = new DragAndDrop()

    val multiplexer: InputMultiplexer = new InputMultiplexer()

    multiplexer.addProcessor(0, new InputProcessor {
      override def keyDown(keycode: Int): Boolean = {
        val project: Project = getFrontendProject
        val ep: EditPanel = getFrontendEditPanel
        // 无需项目的 TopActions
        if (App.shortcutManager.isActive(TopBar.TopActions.NEW)) {
          topBar.performNew()
          true
        } else if (App.shortcutManager.isActive(TopBar.TopActions.OPEN)) {
          topBar.performOpen()
          true
        } else if (App.shortcutManager.isActive(TopBar.TopActions.CLOSE)) {
          topBar.performClose()
          true
        } else if (project == null || project.undoManager == null) {
          false
        } else if (App.shortcutManager.isActive(TopBar.TopActions.SAVE)) {
          // 需要项目的 TopActions
          topBar.performSave()
          true
        } else if (App.shortcutManager.isActive(TopBar.TopActions.SAVE_AS)) {
          topBar.performSaveAs()
          true
        } else if (isTextInputFocused) {
          false
        } else if (App.shortcutManager.isActive(TimelineView.Actions.COPY)) {
          // 复制（全局，无需项目）
          App.copyManager.copy()
          true
        } else if (App.shortcutManager.isActive(TimelineView.Actions.UNDO)) {
          // 撤销 / 重做
          project.undoManager.undo()
          if (ep != null) ep.getTimelineView.markTimelineDirty()
          true
        } else if (App.shortcutManager.isActive(TimelineView.Actions.REDO)) {
          project.undoManager.redo()
          if (ep != null) ep.getTimelineView.markTimelineDirty()
          true
        } else {
          false
        }
      }

      override def keyUp(keycode: Int): Boolean = false

      override def keyTyped(character: Char): Boolean = false

      override def touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false

      override def touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false

      override def touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = false

      override def mouseMoved(screenX: Int, screenY: Int): Boolean = false

      override def scrolled(amountX: Float, amountY: Float): Boolean = false

      override def touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    })

    VisUI.load(injectChineseFont(VisUI.SkinScale.X2))
    stage = new Stage(new ScreenViewport())
    stage.addCaptureListener(new InputListener {
      override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
        // 文本输入控件自己会接管键盘焦点，这里再抢一次会让它立刻失焦
        if (button == Input.Buttons.LEFT && !TextInputs.contains(event.getTarget)) {
          var target: Actor = event.getTarget
          while (target != null && !target.isInstanceOf[Focusable]) {
            target = target.getParent
          }
          if (target != null) {
            val focusTarget: Actor = target
            Gdx.app.postRunnable(() => {
              stage.setKeyboardFocus(focusTarget)
              stage.setScrollFocus(focusTarget)
            })
          }
        }
        false
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
      override def getMinHeight: Float = {
        0
      }
      override def getMinWidth: Float = {
        0
      }
      override def getPrefHeight: Float = {
        0
      }
      override def getPrefWidth: Float = {
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
  def getStage: Stage = {
    stage
  }

  def isTextInputFocused: Boolean = {
    if (stage == null) {
      false
    } else {
      val focus = stage.getKeyboardFocus
      focus.isInstanceOf[TextField] || focus.isInstanceOf[VisTextField]
    }
  }

  def getMainLayout: VisTable = {
    mainLayout
  }
  def getMajorArea: Container[Table] = {
    majorArea
  }
  def getTabbedPane: TopTabbedPane = {
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

  def getFrontendProject: Project = tabbedPane.getActiveTab match {
    case tab: ProjectTab => tab.getProject
    case _ => null
  }

  def getFrontendEditPanel: EditPanel = tabbedPane.getActiveTab match {
    case tab: ProjectTab => tab.getEditPanel
    case _ => null
  }

  private def registerDefaultShortcuts(): Unit = {
    for (action <- TimelineView.Actions.values) {
      App.shortcutManager.register(action, action.defaultKeys()*)
    }
    for (action <- TopBar.TopActions.values) {
      App.shortcutManager.register(action, action.defaultKeys()*)
    }
    App.shortcutManager.load()
    topBar.applyCustomShortcuts()
  }

  def getDragAndDrop: DragAndDrop = {
    dragAndDrop
  }

  def getShapeDrawer: ShapeDrawer = {
    shapeDrawer
  }

  def getToastManager: ToastManager = {
    toastManager
  }
}
