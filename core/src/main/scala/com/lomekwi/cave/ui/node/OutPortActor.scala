package com.lomekwi.cave.ui.node

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node

class OutPortActor(port0: Node.OutPort[?]) extends Actor {
  private final val port: Node.OutPort[?] = port0

  def getPort: Node.OutPort[?] = port

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    super.draw(batch, parentAlpha)
    App.root.getShapeDrawer.filledCircle(getX + getWidth, getY + getHeight / 2f, OutPortActor.DOT_RADIUS, Color.WHITE)
  }
}

object OutPortActor {
  private final val DOT_RADIUS: Float = 5f
}
