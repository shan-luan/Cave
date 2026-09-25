package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.graphics.g2d.Batch
import com.kotcrab.vis.ui.VisUI
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.text.TextGenerator

class TlTextSrcActor(source: Source[?]) extends TlSrcActor(source) {

  override def drawContent(batch: Batch, parentAlpha: Float, visibleStartX: Float, visibleEndX: Float): Unit = {
    super.drawContent(batch, parentAlpha, visibleStartX, visibleEndX)
    var text: String = getSource.getGenerator.asInstanceOf[TextGenerator].getText
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
