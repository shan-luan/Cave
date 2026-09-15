package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener

import com.badlogic.gdx.Input.Keys.*

import com.lomekwi.cave.app.App

/** 时间线空白区输入监听器 —— 处理空白点击、播放头刷动、滚轮、右键菜单与快捷键。 */
class TlGroupInputListener(private final val tlGroup: TlGroup) extends InputListener {

  override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
    if (button == Input.Buttons.LEFT && !tlGroup.marqueeActive) {
      tlGroup.clearSelection()
      tlGroup.playhead.seek(Math.max(tlGroup.xToAbsoluteTime(x), 0))
      return true
    }
    if (button == Input.Buttons.RIGHT && event.getTarget.eq(event.getListenerActor)) {
      tlGroup.tlGroupMenu.setContext(Math.max(tlGroup.xToAbsoluteTime(x), 0))
      return true
    }
    false
  }

  override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
    if (button == Input.Buttons.RIGHT && event.getTarget.eq(event.getListenerActor)) {
      tlGroup.tlGroupMenu.showMenu(tlGroup.getStage, event.getStageX, event.getStageY)
    }
  }

  override def touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
    if (tlGroup.marqueeActive) return
    tlGroup.playhead.seek(Math.max(tlGroup.xToAbsoluteTime(x), 0))
  }

  override def scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean = {
    val ip: Input = Gdx.input

    if (ip.isKeyPressed(CONTROL_LEFT) && ip.isKeyPressed(SHIFT_LEFT)) {
      tlGroup.view.adjustTrackHeight(amountY * 10)

    } else if (ip.isKeyPressed(CONTROL_LEFT)) {
      tlGroup.view.scrollVertical(amountY * 10)

    } else if (ip.isKeyPressed(SHIFT_LEFT)) {
      tlGroup.view.scrollHorizontal(amountY * 30, tlGroup.getWidth)

    } else {
      if (!tlGroup.view.zoom(amountY, x / tlGroup.getWidth)) return true
    }

    tlGroup.dirty = true
    true
  }

  override def keyDown(event: InputEvent, keycode: Int): Boolean = {
    if (App.shortcutManager.isActive(TlGroup.Actions.PLAY_PAUSE)) {
      tlGroup.playhead.setPlaying(!tlGroup.playhead.isPlaying)
      return true
    }
    if (App.shortcutManager.isActive(TlGroup.Actions.SPLIT)) {
      tlGroup.splitAtCursor()
      return true
    }
    if (App.shortcutManager.isActive(TlGroup.Actions.DELETE)) {
      tlGroup.deleteSelected()
      return true
    }
    if (App.shortcutManager.isActive(TlGroup.Actions.GROUP)) {
      tlGroup.groupSelectedSegments()
      return true
    }
    if (App.shortcutManager.isActive(TlGroup.Actions.PASTE)) {
      tlGroup.performPaste()
      return true
    }
    true
  }
}
