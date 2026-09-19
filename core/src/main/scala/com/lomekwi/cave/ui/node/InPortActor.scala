package com.lomekwi.cave.ui.node

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node

class InPortActor(port0: Node.InPort[?], editor: PortEditor & Actor) extends Container[Actor] {
  private final val port: Node.InPort[?] = port0

  setActor(editor)

  def getPort: Node.InPort[?] = port

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    super.draw(batch, parentAlpha)
    App.root.getShapeDrawer.filledCircle(getX, getY + getHeight / 2f, InPortActor.DOT_RADIUS, Color.WHITE)
  }
}

object InPortActor {
  private final val DOT_RADIUS: Float = 5f
}
