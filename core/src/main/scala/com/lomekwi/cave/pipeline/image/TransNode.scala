package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

class TransNode extends Filter[Transformable] {

  private final val dx: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("位移 X".in(Zh) | "Offset X".in(En)).str(), 0.0, classOf[Double]))
  private final val dy: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("位移 Y".in(Zh) | "Offset Y".in(En)).str(), 0.0, classOf[Double]))
  private final val scaleX: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("缩放 X".in(Zh) | "Scale X".in(En)).str(), 1.0, classOf[Double]))
  private final val scaleY: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("缩放 Y".in(Zh) | "Scale Y".in(En)).str(), 1.0, classOf[Double]))
  private final val dRotation: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("旋转".in(Zh) | "Rotation".in(En)).str(), 0.0, classOf[Double]))

  private var flipXState: Boolean = false
  private var flipYState: Boolean = false

  private final val in: FilterIn = addInPort(new FilterIn(("输入".in(Zh) | "Input".in(En)).str()) {
  })

  private final val out: FilterOut = addOutPort(new FilterOut(("输出".in(Zh) | "Output".in(En)).str()) {
    override def getData: Transformable = {
      val frame: Transformable = filterIn.getData
      if (frame != null) {
        var t: Transform = frame.transform
        if (t == null) {
          t = new Transform()
          frame.transform = t
        }
        t.applyLocal(TransNode.value(dx).toFloat, TransNode.value(dy).toFloat, TransNode.value(scaleX).toFloat, TransNode.value(scaleY).toFloat,
                TransNode.value(dRotation).toFloat, flipXState, flipYState)
      }
      frame
    }
  })

  def this(dx: Double, dy: Double, scaleX: Double, scaleY: Double, dRotation: Double) = {
    this()
    setDx(dx)
    setDy(dy)
    setScaleX(scaleX)
    setScaleY(scaleY)
    setDRotation(dRotation)
  }

  def getDx: Double = {
    TransNode.value(dx)
  }

  def setDx(v: Double): Unit = {
    dx.defaultData = v
  }

  def getDy: Double = {
    TransNode.value(dy)
  }

  def setDy(v: Double): Unit = {
    dy.defaultData = v
  }

  def getScaleX: Double = {
    TransNode.value(scaleX)
  }

  def setScaleX(v: Double): Unit = {
    scaleX.defaultData = v
  }

  def getScaleY: Double = {
    TransNode.value(scaleY)
  }

  def setScaleY(v: Double): Unit = {
    scaleY.defaultData = v
  }

  def getDRotation: Double = {
    TransNode.value(dRotation)
  }

  def setDRotation(v: Double): Unit = {
    dRotation.defaultData = v
  }

  def flipX(): Boolean = {
    flipXState
  }

  def flipX(v: Boolean): Unit = {
    flipXState = v
  }

  def flipY(): Boolean = {
    flipYState
  }

  def flipY(v: Boolean): Unit = {
    flipYState = v
  }

  override val name: String = ("变换".in(Zh) | "Transform".in(En)).str()
}

object TransNode {
  private def value(p: Node.InPort[Double]): Double = {
    p.getData
  }
}
