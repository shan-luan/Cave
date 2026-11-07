package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.ui.editpanel.previewarea.ImgFrameActor

/**
 * 持有两个 [[Renderable]] 的帧。
 */
@SerialVersionUID(1L)
class BiRenderFrame(trackIndex: Int, var a: Renderable, var b: Renderable) extends Frame(trackIndex) with Renderable {

  override protected def createActor(): Actor = new ImgFrameActor(this)

  override def render(batch: Batch): Unit = {
    if (a != null) renderSide(a, batch)
    if (b != null) renderSide(b, batch)
  }

  private def renderSide(side: Renderable, batch: Batch): Unit = {
    val own = side.opacity
    side.opacity = own * opacity
    side.render(batch)
    side.opacity = own
  }
}
