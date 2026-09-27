package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.graphics.g2d.Batch

trait Renderable {
  var opacity: Float = 1f
  def render(batch: Batch): Unit
}
