package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener

import com.badlogic.gdx.Input.Keys.*

import com.lomekwi.cave.app.App

/** 时间线空白区输入监听器：处理空白点击、播放头刷动、滚轮、右键菜单与快捷键。 */
class TlInputListener(private final val timelineView: TimelineView) extends InputListener {

  override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
    if (button == Input.Buttons.LEFT && !timelineView.marqueeActive) {
      timelineView.clearSelection()
      timelineView.playhead.seek(Math.max(timelineView.xToAbsoluteTime(x), 0))
      true
    } else if (button == Input.Buttons.RIGHT && event.getTarget.eq(event.getListenerActor)) {
      timelineView.viewMenu.setContext(Math.max(timelineView.xToAbsoluteTime(x), 0))
      true
    } else {
      false
    }
  }

  override def touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Unit = {
    if (button == Input.Buttons.RIGHT && event.getTarget.eq(event.getListenerActor)) {
      timelineView.viewMenu.showMenu(timelineView.getStage, event.getStageX, event.getStageY)
    }
  }

  override def touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
    if (!timelineView.marqueeActive) {
      timelineView.playhead.seek(Math.max(timelineView.xToAbsoluteTime(x), 0))
    }
  }

  override def scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean = {
    val ip: Input = Gdx.input

    val handled: Boolean = if (ip.isKeyPressed(CONTROL_LEFT) && ip.isKeyPressed(SHIFT_LEFT)) {
      timelineView.view.adjustTrackHeight(amountY * 10)
      true
    } else if (ip.isKeyPressed(CONTROL_LEFT)) {
      timelineView.view.scrollVertical(amountY * 10)
      true
    } else if (ip.isKeyPressed(SHIFT_LEFT)) {
      timelineView.view.scrollHorizontal(amountY * 30, timelineView.getWidth)
      true
    } else {
      timelineView.view.zoom(amountY, x / timelineView.getWidth)
    }

    if (handled) {
      timelineView.dirty = true
    }
    true
  }

  override def keyDown(event: InputEvent, keycode: Int): Boolean = {
    if (App.shortcutManager.isActive(TimelineView.Actions.PLAY_PAUSE)) {
      timelineView.playhead.setPlaying(!timelineView.playhead.isPlaying)
    } else if (App.shortcutManager.isActive(TimelineView.Actions.SPLIT)) {
      timelineView.splitAtCursor()
    } else if (App.shortcutManager.isActive(TimelineView.Actions.DELETE)) {
      timelineView.deleteSelected()
    } else if (App.shortcutManager.isActive(TimelineView.Actions.GROUP)) {
      timelineView.groupSelectedSources()
    } else if (App.shortcutManager.isActive(TimelineView.Actions.PASTE)) {
      timelineView.performPaste()
    }
    true
  }
}
