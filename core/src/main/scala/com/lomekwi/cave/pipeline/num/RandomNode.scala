package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.Node

/** 输出端口每次求值返回 [0,1) 的随机数。 */
@SerialVersionUID(1L)
final class RandomNode extends Node {
  final val out: Node.OutPort[Double] = addOutPort(new Node.OutPort[Double]("输出", classOf[Double]) {
    override def getData: Double = Math.random()
  })

  override val name: String = "随机数"
}
