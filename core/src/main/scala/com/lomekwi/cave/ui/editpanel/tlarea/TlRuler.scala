package com.lomekwi.cave.ui.editpanel.tlarea

import com.lomekwi.cave.util.Units.{SECOND, niceScale}

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.kotcrab.vis.ui.VisUI
import com.lomekwi.cave.app.App

class TlRuler(private final val tlGroup: TlGroup) extends Widget {
  private final val font: BitmapFont = VisUI.getSkin.getFont("default-font")
  private final val sb: java.lang.StringBuilder = new java.lang.StringBuilder(8)


  addListener(new InputListener {
    override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
      if (button == Input.Buttons.LEFT) {
        tlGroup.clearSelection()
        tlGroup.seekPlayheadAtX(x)
        return true
      }
      false
    }
    override def touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int): Unit = {
      tlGroup.seekPlayheadAtX(x)
    }
  })

  override def act(delta: Float): Unit = {
    super.act(delta)
    if (App.shortcutManager.isActive(TlGroup.Actions.SEEK)) {
      val pointer: Vector2 = new Vector2(Gdx.input.getX.toFloat, Gdx.input.getY.toFloat)
      getStage.screenToStageCoordinates(pointer)
      stageToLocalCoordinates(pointer)
      if (pointer.x >= 0 && pointer.x <= getWidth && pointer.y >= 0 && pointer.y <= getHeight) {
        tlGroup.seekPlayheadAtX(pointer.x)
      }
    }
  }

  override def getPrefHeight(): Float = {
    16
  }

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    super.draw(batch, parentAlpha)
    App.root.getShapeDrawer().filledRectangle(getX, getY, getWidth, getHeight, Color.DARK_GRAY)

    val interval: Long = niceScale((tlGroup.view.durationTime * TlRuler.PIXELS_PER_TICK / getWidth).toLong)
    val start: Long = (tlGroup.view.startTime / interval) * interval

    var t = start
    while (t < tlGroup.view.startTime + tlGroup.view.durationTime) {
      val x: Float = tlGroup.absoluteTimeToX(t) + getX
      App.root.getShapeDrawer().filledRectangle(x, getY, 1, getHeight, Color.WHITE)
      formatTime(t, interval)
      font.setColor(Color.WHITE)
      font.draw(batch, sb, x + 2, getY + getHeight - 2)
      t += interval
    }
  }

  private def formatTime(t: Long, interval: Long): Unit = {
    var seconds: Long = t / SECOND
    val minutes: Long = seconds / 60
    seconds = seconds % 60
    sb.setLength(0)
    if (minutes < 10) sb.append('0')
    sb.append(minutes).append(':')
    if (seconds < 10) sb.append('0')
    sb.append(seconds)
    if (interval < SECOND) {
      val cs: Long = (t % SECOND) / 10000
      sb.append('.')
      if (cs < 10) sb.append('0')
      sb.append(cs)
    }
  }
}

object TlRuler {
  private final val PIXELS_PER_TICK: Float = 200f
}
