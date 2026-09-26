package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.BinaryNode

@SerialVersionUID(1L)
final class AddNode extends BinaryNode[Double] {
  private final val inA: In = addInPort(new In("A"))
  private final val inB: In = addInPort(new In("B"))
  private final val out: Out = addOutPort(new Out("输出") {
    override def getData: Double = inA.getData + inB.getData
  })

  inA.setDefaultData(0.0)
  inB.setDefaultData(0.0)

  override def getName: String = "加法"
}
