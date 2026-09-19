package com.lomekwi.cave.ui.node

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node

class InPortActor(port0: Node.InPort[?], editor: PortEditor & Actor) extends Container[Actor] with PortActor {
  private final val port: Node.InPort[?] = port0

  setActor(editor)

  override def getPort: Node.InPort[?] = port

  override def getAnchor(out: Vector2): Vector2 = {
    out.set(getX, getY + getHeight / 2f)
  }

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    super.draw(batch, parentAlpha)
    val drawer = App.root.getShapeDrawer
    drawPendingLink(drawer)
    drawDot(drawer)
  }
}
