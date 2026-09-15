package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.graphics.g2d.Batch
import com.kotcrab.vis.ui.VisUI
import com.lomekwi.cave.pipeline.text.TextSrc
import com.lomekwi.cave.timeline.Segment

class TextSegActor(segment: Segment) extends SegActor(segment) {

  override def drawContent(batch: Batch, parentAlpha: Float, visibleStartX: Float, visibleEndX: Float): Unit = {
    super.drawContent(batch, parentAlpha, visibleStartX, visibleEndX)
    var text: String = getSegment.getSource.asInstanceOf[TextSrc].getText
    if (text != null && text.indexOf('\n') >= 0) {
      text = text.substring(0, text.indexOf('\n'))
    }
    if (text != null && !text.isEmpty) {
      val font = VisUI.getSkin.getFont("default-font")
      val textY: Float = getY + getHeight / 2f + font.getCapHeight / 2f
      font.draw(batch, text, getX + visibleStartX + 4, textY)
    }
  }
}
