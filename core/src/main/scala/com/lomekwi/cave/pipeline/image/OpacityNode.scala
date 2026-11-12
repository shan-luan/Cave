package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.{FilterChain, Node, NodeGraph}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

@SerialVersionUID(1L)
class OpacityNode extends Filter[Renderable] {
  private final val opacity: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("透明度".in(Zh) | "Opacity".in(En)).str(), 1.0, classOf[Double]))

  private final val in: FilterIn = addInPort(new FilterIn(("输入".in(Zh) | "Input".in(En)).str()))

  private final val out: FilterOut = addOutPort(new FilterOut(("输出".in(Zh) | "Output".in(En)).str()) {
    override def getData(context: FilterChain | NodeGraph): Renderable = {
      val frame: Renderable = filterIn.getData(context)
      if (frame != null) {
        frame.opacity *= opacity.getData(context).toFloat
      }
      frame
    }
  })

  def getOpacity: Double = {
    opacity.defaultData
  }

  override val name: String = ("透明度".in(Zh) | "Opacity".in(En)).str()
}
