package com.lomekwi.cave.ui.node

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node

class InPortActor(port0: Node.InPort[?], editor: PortEditor & Actor) extends Container[Actor] with PortActor {
  private final val port: Node.InPort[?] = port0
  private var linked: Boolean = port.isLinked

  setActor(editor)
  syncControl(animate = false)

  override def getPort: Node.InPort[?] = port

  /** 输入端口只有一条连接，拖动即把它从原地提起，落空断开，落在别的输出端口则改接。 */
  override protected def detachForDrag(): PortActor = {
    val prev: Node.OutPort[?] = port.getPrev
    port.unlink()
    if (prev == null) null else findPortActor(prev)
  }

  override def getAnchor(out: Vector2): Vector2 = {
    out.set(getX, getY + getHeight / 2f)
  }

  /** 连接状态改变后编辑控件淡出或淡入，避免跳变。 */
  override def act(delta: Float): Unit = {
    super.act(delta)
    val now: Boolean = port.isLinked
    if (now != linked) {
      linked = now
      syncControl(animate = true)
    }
  }

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    super.draw(batch, parentAlpha)
    val drawer = App.root.getShapeDrawer
    drawPendingLink(drawer)
    drawDot(drawer)
  }

  /** 连接后输入端口不再取默认值，编辑控件失去意义，随之淡出；animate 为 false 时直接对齐状态。 */
  private def syncControl(animate: Boolean): Unit = {
    if (editor == null) {
      return
    }
    val control: Actor = editor.getControl
    control.clearActions()
    if (!animate) {
      control.getColor.a = if (linked) 0f else 1f
      control.setVisible(!linked)
    } else if (linked) {
      control.addAction(Actions.sequence(
        Actions.fadeOut(InPortActor.FADE_TIME, Interpolation.fade),
        Actions.hide()))
    } else {
      control.addAction(Actions.sequence(
        Actions.show(),
        Actions.fadeIn(InPortActor.FADE_TIME, Interpolation.fade)))
    }
  }
}

object InPortActor {
  private final val FADE_TIME: Float = 0.15f
}
