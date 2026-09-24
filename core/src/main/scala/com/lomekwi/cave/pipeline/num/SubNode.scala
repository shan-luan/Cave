package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.Mixer

@SerialVersionUID(1L)
final class SubNode extends Mixer[Double] {
  private final val inA: MixIn = addInPort(new MixIn("A"))
  private final val inB: MixIn = addInPort(new MixIn("B"))
  private final val out: MixOut = addOutPort(new MixOut("输出") {
    override def getData: Double = inA.getData - inB.getData
  })

  inA.setDefaultData(0.0)
  inB.setDefaultData(0.0)

  override def getName: String = "减法"
}
