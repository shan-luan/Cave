package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor

trait Renderable {
  var opacity: Float = 1f
  def render(batch: Batch): Unit

  /** 本帧在预览画布上的可视化壳，随取随建。 */
  @transient lazy val actor: Actor = createActor()

  protected def createActor(): Actor
}
