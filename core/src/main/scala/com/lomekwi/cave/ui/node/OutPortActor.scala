package com.lomekwi.cave.ui.node

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node

import scala.jdk.CollectionConverters.*

class OutPortActor(port0: Node.OutPort[?]) extends Actor with PortActor {
  private final val port: Node.OutPort[?] = port0

  override def getPort: Node.OutPort[?] = port

  override def getAnchor(out: Vector2): Vector2 = {
    out.set(getX + getWidth, getY + getHeight / 2f)
  }

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    super.draw(batch, parentAlpha)
    val drawer = App.root.getShapeDrawer
    for (in <- port.getNext.asScala) {
      val peer: PortActor = findPortActor(in)
      if (peer != null) {
        drawLink(drawer, peer)
      }
    }
    drawPendingLink(drawer)
    drawDot(drawer)
  }
}
