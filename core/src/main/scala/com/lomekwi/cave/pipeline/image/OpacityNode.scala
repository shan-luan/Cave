package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.util.legacy_i18n.I18N.i18n

@SerialVersionUID(1L)
class OpacityNode extends Filter[Renderable] {
  private final val opacity: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](i18n("透明度"), 1.0, classOf[Double]))

  private final val in: FilterIn = addInPort(new FilterIn(i18n("输入")))

  private final val out: FilterOut = addOutPort(new FilterOut(i18n("输出")) {
    override def getData: Renderable = {
      val frame: Renderable = filterIn.getData
      if (frame != null) {
        frame.opacity *= opacity.getData.toFloat
      }
      frame
    }
  })

  def getOpacity: Double = {
    opacity.getData
  }

  def setOpacity(v: Double): Unit = {
    opacity.defaultData = v
  }

  override val name: String = i18n("透明度")
}
