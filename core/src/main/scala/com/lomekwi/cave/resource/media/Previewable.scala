package com.lomekwi.cave.resource.media

import com.badlogic.gdx.graphics.Texture

trait Previewable {
  def getPreview(time: Long): Texture
  def getPreviewInterval: Long
}
