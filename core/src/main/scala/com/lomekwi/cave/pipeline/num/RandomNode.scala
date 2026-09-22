package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.Node

/**
 * 随机数节点：无输入，输出端口每次求值返回一个 [0,1) 的随机数。
 */
@SerialVersionUID(1L)
final class RandomNode extends Node {
  private final val out: Node.OutPort[Double] = addOutPort(new Node.OutPort[Double]("输出", classOf[Double]) {
    override def getData: Double = Math.random()
  })

  def getOut: Node.OutPort[Double] = out

  override def getName: String = "随机数"
}
