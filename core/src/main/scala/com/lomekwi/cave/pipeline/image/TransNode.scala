package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node

class TransNode extends Filter[Transformable] {

  private final val dx: Node.InPort[Double] = addInPort(
    new Node.InPort[Double]("位移 X", 0.0, classOf[Double]))
  private final val dy: Node.InPort[Double] = addInPort(
    new Node.InPort[Double]("位移 Y", 0.0, classOf[Double]))
  private final val scaleX: Node.InPort[Double] = addInPort(
    new Node.InPort[Double]("缩放 X", 1.0, classOf[Double]))
  private final val scaleY: Node.InPort[Double] = addInPort(
    new Node.InPort[Double]("缩放 Y", 1.0, classOf[Double]))
  private final val dRotation: Node.InPort[Double] = addInPort(
    new Node.InPort[Double]("旋转", 0.0, classOf[Double]))

  private var flipXState: Boolean = false
  private var flipYState: Boolean = false

  private final val in: FilterIn = addInPort(new FilterIn("输入") {
  })

  private final val out: FilterOut = addOutPort(new FilterOut("输出") {
    override def getData: Transformable = {
      val frame: Transformable = getFilterIn.getData
      if (frame != null) {
        var t: Transform = frame.getTransform
        if (t == null) {
          t = new Transform()
          frame.setTransform(t)
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
    dx.setDefaultData(v)
  }

  def getDy: Double = {
    TransNode.value(dy)
  }

  def setDy(v: Double): Unit = {
    dy.setDefaultData(v)
  }

  def getScaleX: Double = {
    TransNode.value(scaleX)
  }

  def setScaleX(v: Double): Unit = {
    scaleX.setDefaultData(v)
  }

  def getScaleY: Double = {
    TransNode.value(scaleY)
  }

  def setScaleY(v: Double): Unit = {
    scaleY.setDefaultData(v)
  }

  def getDRotation: Double = {
    TransNode.value(dRotation)
  }

  def setDRotation(v: Double): Unit = {
    dRotation.setDefaultData(v)
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

  override def getName: String = {
    "变换"
  }
}

object TransNode {
  private def value(p: Node.InPort[Double]): Double = {
    p.getData
  }
}
