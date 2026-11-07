package com.lomekwi.cave.ui.editpanel.previewarea

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.lomekwi.cave.pipeline.image.BiRenderFrame

/** 转场帧的预览壳。 */
class ImgFrameActor(frame: BiRenderFrame) extends Actor {

  setTouchable(Touchable.disabled)

  override def draw(batch: Batch, parentAlpha: Float): Unit = {
    super.draw(batch, parentAlpha)
    frame.render(batch)
  }
}
