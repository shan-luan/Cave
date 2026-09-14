package com.lomekwi.cave.pipeline.image

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.num.NumFrame

class TransNode extends Filter[Transformable] {

  private final val dx: Node.InPort[NumFrame] = addInPort(
    new Node.InPort[NumFrame]("位移 X", new NumFrame(null), classOf[NumFrame]))
  private final val dy: Node.InPort[NumFrame] = addInPort(
    new Node.InPort[NumFrame]("位移 Y", new NumFrame(null), classOf[NumFrame]))
  private final val scaleX: Node.InPort[NumFrame] = addInPort(
    new Node.InPort[NumFrame]("缩放 X", TransNode.frame(1), classOf[NumFrame]))
  private final val scaleY: Node.InPort[NumFrame] = addInPort(
    new Node.InPort[NumFrame]("缩放 Y", TransNode.frame(1), classOf[NumFrame]))
  private final val dRotation: Node.InPort[NumFrame] = addInPort(
    new Node.InPort[NumFrame]("旋转", new NumFrame(null), classOf[NumFrame]))

  private var flipXState: Boolean = false
  private var flipYState: Boolean = false

  private final val in: FilterIn = addInPort(new FilterIn("输入") {
  })

  private final val out: FilterOut = addOutPort(new FilterOut("输出") {
    override def getData(): Transformable = {
      val frame: Transformable = getFilterIn().getData()
      if (frame != null) {
        var t: Transform = frame.getTransform()
        if (t == null) {
          t = new Transform()
          frame.setTransform(t)
        }
        t.applyLocal(TransNode.`val`(dx).toFloat, TransNode.`val`(dy).toFloat, TransNode.`val`(scaleX).toFloat, TransNode.`val`(scaleY).toFloat,
                TransNode.`val`(dRotation).toFloat, flipXState, flipYState)
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

  def getDx(): Double = {
    TransNode.`val`(dx)
  }

  def setDx(v: Double): Unit = {
    dx.getDefaultData().setVal(v)
  }

  def getDy(): Double = {
    TransNode.`val`(dy)
  }

  def setDy(v: Double): Unit = {
    dy.getDefaultData().setVal(v)
  }

  def getScaleX(): Double = {
    TransNode.`val`(scaleX)
  }

  def setScaleX(v: Double): Unit = {
    scaleX.getDefaultData().setVal(v)
  }

  def getScaleY(): Double = {
    TransNode.`val`(scaleY)
  }

  def setScaleY(v: Double): Unit = {
    scaleY.getDefaultData().setVal(v)
  }

  def getDRotation(): Double = {
    TransNode.`val`(dRotation)
  }

  def setDRotation(v: Double): Unit = {
    dRotation.getDefaultData().setVal(v)
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

  override def getType(): Class[Transformable] = {
    classOf[Transformable]
  }

  override def getName(): String = {
    "变换"
  }
}

object TransNode {
  private def frame(v: Double): NumFrame = {
    val f = new NumFrame(null)
    f.setVal(v)
    f
  }

  private def `val`(p: Node.InPort[NumFrame]): Double = {
    p.getData().getVal()
  }
}
