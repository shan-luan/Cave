package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.BinaryNode

@SerialVersionUID(1L)
final class DivNode extends BinaryNode[Double] {
  addInPort(new In("A"))
  addInPort(new In("B"))
  addOutPort(new Out("输出") {
    override def getData: Double = inA.getData / inB.getData
  })

  inA.defaultData = 1.0
  inB.defaultData = 1.0

  override val name: String = "除法"
}
