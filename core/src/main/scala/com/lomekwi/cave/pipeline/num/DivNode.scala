package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.BinaryNode

@SerialVersionUID(1L)
final class DivNode extends BinaryNode[Double] {
  private final val inA: In = addInPort(new In("A"))
  private final val inB: In = addInPort(new In("B"))
  private final val out: Out = addOutPort(new Out("输出") {
    override def getData: Double = inA.getData / inB.getData
  })

  inA.setDefaultData(1.0)
  inB.setDefaultData(1.0)

  override def getName: String = "除法"
}
