package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

/** 输出端口每次求值返回 [0,1) 的随机数。 */
@SerialVersionUID(1L)
final class RandomNode extends Node {
  val out: Node.OutPort[Double] = addOutPort(new Node.OutPort[Double](("输出".in(Zh) | "Output".in(En)).str(), classOf[Double]) {
    override def getData: Double = Math.random()
  })

  override val name: String = ("随机数".in(Zh) | "Random Number".in(En)).str()
}
