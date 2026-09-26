package com.lomekwi.cave.ui.editpanel.tlarea

import com.lomekwi.cave.util.Units.niceScale

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.image.VdoGenerator
import com.lomekwi.cave.resource.media.VdoRes
import com.lomekwi.cave.ui.Colors
import space.earlygrey.shapedrawer.ShapeDrawer

class TlVdoSrcActor(source: Source[?]) extends TlSrcActor(source) {

  override def drawContent(batch: Batch, parentAlpha: Float, visibleStartX: Float, visibleEndX: Float): Unit = {
    val sd: ShapeDrawer = App.root.getShapeDrawer
    val contentRange = range
    val contentOrigin = origin
    val srcLocalStart: Long = contentRange.lo - contentOrigin
    val srcLocalEnd: Long = contentRange.hi - contentOrigin
    val srcDuration: Long = srcLocalEnd - srcLocalStart

    sd.filledRectangle(getX, getY, getWidth, getHeight, Colors.ACCENT_LIGHT)

    if (srcDuration > 0) {
      val res: VdoRes = getSource.getGenerator.asInstanceOf[VdoGenerator].getVdoRes

      val pxPerUs: Float = getWidth / srcDuration.toFloat
      val aspect: Float = res.getWidth.toFloat / res.getHeight
      val thumbDisplayW: Float = getHeight * aspect

      var rawStep: Long = (thumbDisplayW / pxPerUs).toLong
      if (rawStep <= 0) rawStep = 1
      val timeStep: Long = niceScale(rawStep)

      val gridOrigin: Long = (srcLocalStart / timeStep) * timeStep
      val absVisibleStart: Long = srcLocalStart + (visibleStartX / pxPerUs).toLong
      val absVisibleEnd: Long = srcLocalStart + (visibleEndX / pxPerUs).toLong
      var firstT: Long = ((absVisibleStart - gridOrigin) / timeStep) * timeStep + gridOrigin
      firstT = Math.max(gridOrigin, firstT)
      val lastT: Long = Math.min(srcLocalEnd, absVisibleEnd)

      var lastRightEdge: Float = Float.NegativeInfinity

      var t: Long = firstT
      while (t < lastT) {
        val tex: Texture = res.getPreview(t)
        if (tex != null) {
          val x: Float = getX + (t - srcLocalStart) * pxPerUs

          if (x + thumbDisplayW > lastRightEdge) {
            batch.draw(tex, x, getY, thumbDisplayW, getHeight)
            lastRightEdge = x + thumbDisplayW
          }
        }
        t += timeStep
      }
    }
  }
}
