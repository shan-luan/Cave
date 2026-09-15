package com.lomekwi.cave.ui.node

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.Colors
import com.lomekwi.cave.ui.Focusable
import com.lomekwi.cave.ui.widget.PanZoomCanvas
import space.earlygrey.shapedrawer.ShapeDrawer

//TODO:WIP
class NodeEditorView extends VisTable with Focusable {
  private final val panZoom: PanZoomCanvas = new PanZoomCanvas(0.1f, 4f, 1000f)
  private final val canvas: Group = panZoom.getCanvas

  setFillParent(true)
  add(panZoom).grow()
  setupListener()
  addTestLabels()

  private def addTestLabels(): Unit = {
    val table = new VisTable()
    table.setFillParent(true)
    table.add(new VisLabel("节点编辑器")).pad(20f).row()
    table.add(new VisLabel("滚轮缩放 / WASD 移动")).row()
    canvas.addActor(table)
  }

  private def setupListener(): Unit = {
    addListener(new ClickListener {
      override def touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean = {
        false
      }

      override def scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean = {
        panZoom.zoomAt(event.getStageX, event.getStageY, amountY)
        true
      }
    })
  }

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    val drawer = App.root.getShapeDrawer
    drawer.filledRectangle(getX, getY, getWidth, getHeight, Colors.NODE_BG)
    drawGrid(drawer)
    super.draw(batch, parentAlpha)
  }

  private def drawGrid(drawer: ShapeDrawer): Unit = {
    val ox = getX + canvas.getX
    val oy = getY + canvas.getY
    val s = canvas.getScaleX
    val spacing = NodeEditorView.GRID_SPACING * s
    if (spacing < NodeEditorView.MIN_GRID_PIXEL) return

    val x0 = getX
    val x1 = getX + getWidth
    val y0 = getY
    val y1 = getY + getHeight

    val startX = Math.ceil(((x0 - ox) / spacing).toDouble).toFloat * spacing
    var x = ox + startX
    while (x <= x1) {
      drawer.line(x, y0, x, y1, Colors.NODE_GRID, 1f)
      x += spacing
    }

    val startY = Math.ceil(((y0 - oy) / spacing).toDouble).toFloat * spacing
    var y = oy + startY
    while (y <= y1) {
      drawer.line(x0, y, x1, y, Colors.NODE_GRID, 1f)
      y += spacing
    }
  }
}

object NodeEditorView {
  private final val GRID_SPACING: Float = 50f
  private final val MIN_GRID_PIXEL: Float = 8f
}
