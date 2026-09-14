package com.lomekwi.cave.ui.widget

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisImageButton
import com.kotcrab.vis.ui.widget.VisTable

/**
 * 一个长的像window的table.
 */
class Card(title: String) extends VisTable {
  private var titleLabel: Label = null
  private var titleTable: Table = null
  private var drawTitleTable: Boolean = false

  {
    val style = VisUI.getSkin.get(classOf[Window.WindowStyle])
    setBackground(style.background)
    setClip(true)

    titleLabel = new Label(title, new Label.LabelStyle(style.titleFont, style.titleFontColor))
    titleLabel.setEllipsis(true)
    titleLabel.setAlignment(VisUI.getDefaultTitleAlign)

    titleTable = new Table {
      override def draw(batch: Batch, parentAlpha: Float): Unit = {
        if (drawTitleTable) super.draw(batch, parentAlpha)
      }
    }
    titleTable.add(titleLabel).growX().minWidth(0)
    addActor(titleTable)
  }

  override protected def drawBackground(batch: Batch, parentAlpha: Float, x: Float, y: Float): Unit = {
    super.drawBackground(batch, parentAlpha, x, y)
    titleTable.getColor.a = getColor.a
    val padTop = getPadTop
    val padLeft = getPadLeft
    titleTable.setSize(getWidth - padLeft - getPadRight, padTop)
    titleTable.setPosition(padLeft, getHeight - padTop)
    drawTitleTable = true
    titleTable.draw(batch, parentAlpha)
    drawTitleTable = false
  }

  override def getPrefWidth(): Float = {
    Math.max(super.getPrefWidth, titleTable.getPrefWidth + getPadLeft + getPadRight)
  }

  def getTitleLabel(): Label = {
    titleLabel
  }

  def getTitleTable(): Table = {
    titleTable
  }

  def addCloseButton(): Unit = {
    val closeButton = new VisImageButton("close-window")
    titleTable.add(closeButton).padRight(-getPadRight + 0.7f)
    closeButton.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        close()
      }
    })
    closeButton.addListener(new ClickListener {
      override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
        event.cancel()
        true
      }
    })
    if (titleLabel.getLabelAlign == Align.center && titleTable.getChildren.size == 2) {
      titleTable.getCell(titleLabel).padLeft(closeButton.getWidth * 2)
    }
  }

  protected def close(): Unit = {
    remove()
  }
}
