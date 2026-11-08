package com.lomekwi.cave.resource.media

import com.badlogic.gdx.graphics.Texture
import com.lomekwi.cave.timeline.Timespan

trait Previewable {
  def getPreview(time: Timespan): Texture
  def getPreviewInterval: Timespan
}
