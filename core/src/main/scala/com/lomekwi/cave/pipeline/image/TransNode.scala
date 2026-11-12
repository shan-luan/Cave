package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.{FilterChain, Node, NodeGraph}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

class TransNode(dx0: Double, dy0: Double, scaleX0: Double, scaleY0: Double, dRotation0: Double) extends Filter[Transformable] {

  def this() = this(0.0, 0.0, 1.0, 1.0, 0.0)

  final val dx: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("位移 X".in(Zh) | "Offset X".in(En)).str(), dx0, classOf[Double]))
  final val dy: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("位移 Y".in(Zh) | "Offset Y".in(En)).str(), dy0, classOf[Double]))
  final val scaleX: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("缩放 X".in(Zh) | "Scale X".in(En)).str(), scaleX0, classOf[Double]))
  final val scaleY: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("缩放 Y".in(Zh) | "Scale Y".in(En)).str(), scaleY0, classOf[Double]))
  final val dRotation: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("旋转".in(Zh) | "Rotation".in(En)).str(), dRotation0, classOf[Double]))

  final val flipXState: Boolean = false
  final val flipYState: Boolean = false

  private final val in: FilterIn = addInPort(new FilterIn(("输入".in(Zh) | "Input".in(En)).str()) {
  })

  private final val out: FilterOut = addOutPort(new FilterOut(("输出".in(Zh) | "Output".in(En)).str()) {
    override def getData(context: FilterChain | NodeGraph): Transformable = {
      val frame: Transformable = filterIn.getData(context)
      if (frame != null) {
        var t: Transform = frame.transform
        if (t == null) {
          t = new Transform()
          frame.transform = t
        }
        t.applyLocal(dx.getData(context).toFloat, dy.getData(context).toFloat, scaleX.getData(context).toFloat, scaleY.getData(context).toFloat,
                dRotation.getData(context).toFloat, flipXState, flipYState)
      }
      frame
    }
  })

  def getDx: Double = dx.defaultData

  def getDy: Double = dy.defaultData

  def getScaleX: Double = scaleX.defaultData

  def getScaleY: Double = scaleY.defaultData

  def getDRotation: Double = dRotation.defaultData

  def flipX(): Boolean = flipXState

  def flipY(): Boolean = flipYState

  override val name: String = ("变换".in(Zh) | "Transform".in(En)).str()
}
