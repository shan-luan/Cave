package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.ui.editpanel.previewarea.ImgFrameActor

/**
 * 持有两个 [[Renderable]] 的帧。[[render]] 时按持有顺序依次绘制，[[b]] 画在 [[a]] 之上，
 * 持有为 null 的子项跳过。自身 [[Renderable.opacity]] 作为系数乘进每个子帧的 opacity
 * 参与渲染，子帧的原值在绘制后还原，画面表现仍由子 [[Renderable]] 各自的 [[Renderable.render]] 决定。
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
