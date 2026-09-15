package com.lomekwi.cave.ui.settings

import com.lomekwi.cave.util.i18n.I18N.i18n

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisScrollPane
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.lomekwi.cave.app.App
import com.lomekwi.cave.app.shortcut.ShortcutAction

import java.util
import java.util.Collections

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

class ShortcutKeysTable extends EntryTable {

  private final val listTable: VisTable = new VisTable()
  private var recordingAction: ShortcutAction = uninitialized
  private var recordingButton: VisTextButton = uninitialized

  private final val recordingProcessor: InputProcessor = new InputProcessor {
    override def keyDown(keycode: Int): Boolean = {
      if (recordingAction == null) return false
      if (keycode == Input.Keys.ESCAPE) {
        finishRecording(false)
        return true
      }
      if (ShortcutKeysTable.isModifier(keycode)) return true

      val keys: util.List[Integer] = new util.ArrayList[Integer]()
      if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))
        keys.add(Input.Keys.CONTROL_LEFT)
      if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))
        keys.add(Input.Keys.SHIFT_LEFT)
      if (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT))
        keys.add(Input.Keys.ALT_LEFT)
      keys.add(keycode)

      App.shortcutManager.register(recordingAction, keys.stream().mapToInt((i: Integer) => i).toArray()*)
      App.shortcutManager.persist()
      finishRecording(true)
      true
    }

    override def keyUp(keycode: Int): Boolean = false
    override def keyTyped(character: Char): Boolean = false
    override def touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    override def touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    override def touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = false
    override def touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean = false
    override def mouseMoved(screenX: Int, screenY: Int): Boolean = false
    override def scrolled(amountX: Float, amountY: Float): Boolean = false
  }

  {
    rebuild()
  }

  private def rebuild(): Unit = {
    clear()
    top().left()

    add(new VisLabel(i18n("快捷键设置"))).pad(10).row()

    val header = new VisTable()
    header.add(new VisLabel(i18n("动作"))).pad(5).width(200)
    header.add(new VisLabel(i18n("快捷键"))).pad(5).width(280)
    listTable.clear()
    listTable.top().left()
    listTable.add(header).fillX().row()

    for (action <- App.shortcutManager.getAllActions.asScala) {
      val row = new VisTable()
      row.add(new VisLabel(action.displayName())).pad(5).width(200)

      val keyBtn = new VisTextButton(keyDisplay(action))
      keyBtn.addListener(new ChangeListener {
        override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
          startRecording(action, keyBtn)
        }
      })
      row.add(keyBtn).pad(5).width(280)

      val resetBtn = new VisTextButton(i18n("重置"))
      resetBtn.setDisabled(!ShortcutKeysTable.hasCustomKeys(action))
      resetBtn.addListener(new ChangeListener {
        override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
          if (recordingAction != null) stopRecording()
          App.shortcutManager.resetToDefault(action)
          App.shortcutManager.persist()
          rebuild()
        }
      })
      row.add(resetBtn).pad(5).width(60)

      listTable.add(row).fillX().padBottom(2).row()
    }

    val scrollPane = new VisScrollPane(listTable)
    scrollPane.setForceScroll(false, true)
    scrollPane.setFadeScrollBars(false)
    add(scrollPane).grow()
  }

  private def startRecording(action: ShortcutAction, btn: VisTextButton): Unit = {
    if (recordingAction != null) {
      recordingButton.setText(keyDisplay(recordingAction))
      stopRecording()
    }
    recordingAction = action
    recordingButton = btn
    btn.setText(i18n("按下新快捷键..."))
    Gdx.input.getInputProcessor match {
      case m: InputMultiplexer => m.addProcessor(0, recordingProcessor)
      case _ =>
    }
  }

  private def stopRecording(): Unit = {
    recordingAction = null
    recordingButton = null
    Gdx.input.getInputProcessor match {
      case m: InputMultiplexer => m.removeProcessor(recordingProcessor)
      case _ =>
    }
  }

  private def finishRecording(apply: Boolean): Unit = {
    stopRecording()
    rebuild()
  }

  private def keyDisplay(action: ShortcutAction): String = {
    val keys: util.Collection[Integer] = App.shortcutManager.getKeys(action)
    if (keys.isEmpty) return i18n("未设置")
    val list: util.List[Integer] = new util.ArrayList[Integer](keys)
    list.sort((a: Integer, b: Integer) => {
      val aMod = if (ShortcutKeysTable.isModifier(a)) 0 else 1
      val bMod = if (ShortcutKeysTable.isModifier(b)) 0 else 1
      if (aMod != bMod) aMod - bMod else 0
    })
    val sb = new StringBuilder()
    for (k <- list.asScala) {
      if (sb.length() > 0) sb.append(" + ")
      sb.append(ShortcutKeysTable.keyName(k))
    }
    sb.toString
  }

  override def getName: String = {
    i18n("快捷键")
  }
}

object ShortcutKeysTable {

  private def hasCustomKeys(action: ShortcutAction): Boolean = {
    val current: util.Collection[Integer] = App.shortcutManager.getKeys(action)
    val defaults = action.defaultKeys()
    if (current.size() != defaults.length) return true
    val curSorted: util.List[Integer] = new util.ArrayList[Integer](current)
    Collections.sort(curSorted)
    val defSorted = defaults.clone()
    util.Arrays.sort(defSorted)
    var i = 0
    val it = curSorted.iterator()
    while (it.hasNext) {
      val k = it.next()
      if (k != defSorted(i)) return true
      i += 1
    }
    false
  }

  private def isModifier(keycode: Int): Boolean = {
    keycode == Input.Keys.CONTROL_LEFT || keycode == Input.Keys.CONTROL_RIGHT
      || keycode == Input.Keys.SHIFT_LEFT || keycode == Input.Keys.SHIFT_RIGHT
      || keycode == Input.Keys.ALT_LEFT || keycode == Input.Keys.ALT_RIGHT
  }

  private def keyName(keycode: Int): String = {
    if (keycode >= Input.Keys.A && keycode <= Input.Keys.Z)
      return String.valueOf(('A' + (keycode - Input.Keys.A)).toChar)
    if (keycode >= Input.Keys.NUM_0 && keycode <= Input.Keys.NUM_9)
      return String.valueOf(('0' + (keycode - Input.Keys.NUM_0)).toChar)
    keycode match {
      case Input.Keys.CONTROL_LEFT | Input.Keys.CONTROL_RIGHT => "Ctrl"
      case Input.Keys.SHIFT_LEFT | Input.Keys.SHIFT_RIGHT => "Shift"
      case Input.Keys.ALT_LEFT | Input.Keys.ALT_RIGHT => "Alt"
      case Input.Keys.SPACE => i18n("空格")
      case Input.Keys.DEL | Input.Keys.FORWARD_DEL => "Del"
      case Input.Keys.ESCAPE => "Esc"
      case Input.Keys.TAB => "Tab"
      case Input.Keys.ENTER => "Enter"
      case Input.Keys.UP => i18n("上")
      case Input.Keys.DOWN => i18n("下")
      case Input.Keys.LEFT => i18n("左")
      case Input.Keys.RIGHT => i18n("右")
      case Input.Keys.F1 => "F1"
      case Input.Keys.F2 => "F2"
      case Input.Keys.F3 => "F3"
      case Input.Keys.F4 => "F4"
      case Input.Keys.F5 => "F5"
      case Input.Keys.F6 => "F6"
      case Input.Keys.F7 => "F7"
      case Input.Keys.F8 => "F8"
      case Input.Keys.F9 => "F9"
      case Input.Keys.F10 => "F10"
      case Input.Keys.F11 => "F11"
      case Input.Keys.F12 => "F12"
      case Input.Keys.MINUS => "-"
      case Input.Keys.EQUALS => "="
      case Input.Keys.COMMA => ","
      case Input.Keys.PERIOD => "."
      case Input.Keys.SEMICOLON => ";"
      case Input.Keys.APOSTROPHE => "'"
      case Input.Keys.SLASH => "/"
      case Input.Keys.LEFT_BRACKET => "["
      case Input.Keys.RIGHT_BRACKET => "]"
      case Input.Keys.BACKSLASH => "\\"
      case Input.Keys.GRAVE => "`"
      case _ => "Key#" + keycode
    }
  }
}
