package com.lomekwi.cave.ui.editpanel.tlarea

import com.lomekwi.cave.util.Units.niceScale

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.image.ImgSrc
import com.lomekwi.cave.resource.media.ImgRes
import com.lomekwi.cave.ui.Colors
import com.lomekwi.cave.timeline.Segment
import space.earlygrey.shapedrawer.ShapeDrawer

class ImgSegActor(segment: Segment) extends SegActor(segment) {

  override def drawContent(batch: Batch, parentAlpha: Float, visibleStartX: Float, visibleEndX: Float): Unit = {
    val sd: ShapeDrawer = App.root.getShapeDrawer()
    val seg: Segment = getSegment()
    val range = seg.getRange()
    val segLocalStart: Long = range.lowerEndpoint() - seg.getOrigin()
    val segLocalEnd: Long = range.upperEndpoint() - seg.getOrigin()
    val segDuration: Long = segLocalEnd - segLocalStart

    sd.filledRectangle(getX, getY, getWidth, getHeight, Colors.ACCENT_LIGHT)

    if (segDuration > 0) {
      val res: ImgRes = seg.getSource().asInstanceOf[ImgSrc].getImgRes()

      val pxPerUs: Float = getWidth / segDuration.toFloat
      val aspect: Float = res.getWidth().toFloat / res.getHeight()
      val thumbDisplayW: Float = getHeight * aspect

      var rawStep: Long = (thumbDisplayW / pxPerUs).toLong
      if (rawStep <= 0) rawStep = 1
      val timeStep: Long = niceScale(rawStep)

      val gridOrigin: Long = (segLocalStart / timeStep) * timeStep
      val absVisibleStart: Long = segLocalStart + (visibleStartX / pxPerUs).toLong
      val absVisibleEnd: Long = segLocalStart + (visibleEndX / pxPerUs).toLong
      var firstT: Long = ((absVisibleStart - gridOrigin) / timeStep) * timeStep + gridOrigin
      firstT = Math.max(gridOrigin, firstT)
      val lastT: Long = Math.min(segLocalEnd, absVisibleEnd)

      var lastRightEdge: Float = Float.NegativeInfinity

      var t: Long = firstT
      while (t < lastT) {
        val tex: Texture = res.getPreview(t)
        if (tex != null) {
          val x: Float = getX + (t - segLocalStart) * pxPerUs

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
