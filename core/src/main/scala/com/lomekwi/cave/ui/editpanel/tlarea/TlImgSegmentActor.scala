package com.lomekwi.cave.ui.editpanel.tlarea

import com.lomekwi.cave.util.niceScale

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.image.ImgSource
import com.lomekwi.cave.resource.media.ImgRes
import com.lomekwi.cave.timeline.{Segment, Timespan}
import com.lomekwi.cave.timeline.Timespan.*
import com.lomekwi.cave.ui.Colors
import space.earlygrey.shapedrawer.ShapeDrawer

class TlImgSegmentActor(segment: Segment) extends TlSegmentActor(segment) {

  override def drawContent(batch: Batch, parentAlpha: Float, visibleStartX: Float, visibleEndX: Float): Unit = {
    val sd: ShapeDrawer = App.root.shapeDrawer
    val contentInterval = interval
    val contentOrigin = origin
    val srcLocalStart: Timespan = contentInterval.lo - contentOrigin
    val srcLocalEnd: Timespan = contentInterval.hi - contentOrigin
    val srcDuration: Timespan = srcLocalEnd - srcLocalStart

    sd.filledRectangle(getX, getY, getWidth, getHeight, Colors.ACCENT_LIGHT)

    if (srcDuration > Timespan.Zero) {
      val res: ImgRes = segment.source.asInstanceOf[ImgSource].imgRes

      val pxPerUs: Float = getWidth / srcDuration.value.toFloat
      val aspect: Float = res.width.toFloat / res.height
      val thumbDisplayW: Float = getHeight * aspect

      var rawStep: Long = (thumbDisplayW / pxPerUs).toLong
      if (rawStep <= 0) rawStep = 1
      val timeStep: Timespan = Timespan(niceScale(rawStep))

      val gridOrigin: Timespan = srcLocalStart.floorTo(timeStep)
      val absVisibleStart: Timespan = srcLocalStart + (visibleStartX / pxPerUs).micros
      val absVisibleEnd: Timespan = srcLocalStart + (visibleEndX / pxPerUs).micros
      var firstT: Timespan = (absVisibleStart - gridOrigin).floorTo(timeStep) + gridOrigin
      firstT = gridOrigin.max(firstT)
      val lastT: Timespan = srcLocalEnd.min(absVisibleEnd)

      var lastRightEdge: Float = Float.NegativeInfinity

      var t: Timespan = firstT
      while (t < lastT) {
        val tex: Texture = res.getPreview(t)
        if (tex != null) {
          val x: Float = getX + (t - srcLocalStart).value * pxPerUs

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
