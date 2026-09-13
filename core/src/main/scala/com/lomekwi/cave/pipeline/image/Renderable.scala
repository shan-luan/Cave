package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.graphics.g2d.Batch

trait Renderable {
  def render(batch: Batch): Unit
}
