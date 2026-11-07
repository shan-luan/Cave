package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.graphics.g2d.Batch
import com.lomekwi.cave.timeline.Segment

/** 阻挡片段的 actor。 */
class TlBlockSegmentActor(segment: Segment) extends TlSegmentActor(segment) {

  override def drawContent(batch: Batch, parentAlpha: Float, visibleStartX: Float, visibleEndX: Float): Unit = {}
}
