package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.graphics.g2d.Batch
import com.lomekwi.cave.pipeline.Segment

/** 阻挡片段的 actor。它位于可视区之外，不绘制任何内容。 */
class TlBlockSegmentActor(segment: Segment[?]) extends TlSegmentActor(segment) {

  override def drawContent(batch: Batch, parentAlpha: Float, visibleStartX: Float, visibleEndX: Float): Unit = {}
}
