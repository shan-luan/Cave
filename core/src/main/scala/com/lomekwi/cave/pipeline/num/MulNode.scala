package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.Mixer

/** 乘法节点：输出 A * B。 */
@SerialVersionUID(1L)
final class MulNode extends Mixer[Double] {
  private final val inA: MixIn = addInPort(new MixIn("A"))
  private final val inB: MixIn = addInPort(new MixIn("B"))
  private final val out: MixOut = addOutPort(new MixOut("输出") {
    override def getData: Double = inA.getData * inB.getData
  })

  inA.setDefaultData(1.0)
  inB.setDefaultData(1.0)

  override def getType: Class[Double] = classOf[Double]

  override def getName: String = "乘法"
}
