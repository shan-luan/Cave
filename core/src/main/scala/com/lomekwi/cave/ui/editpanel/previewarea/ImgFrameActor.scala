package com.lomekwi.cave.ui.editpanel.previewarea

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.lomekwi.cave.pipeline.image.BiRenderFrame

/**
 * 转场帧的预览壳。draw 时把帧原样画到画布上。
 * 与 [[TransFrameActor]] 不同，帧没有变换与操控点，actor 也不参与命中。
 */
class ImgFrameActor(frame: BiRenderFrame) extends Actor {

  setTouchable(Touchable.disabled)

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    super.draw(batch, parentAlpha)
    frame.render(batch)
  }
}
