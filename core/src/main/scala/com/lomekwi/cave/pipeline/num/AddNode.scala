package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.BinaryNode
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

@SerialVersionUID(1L)
final class AddNode extends BinaryNode[Double] {
  addInPort(new In("A"))
  addInPort(new In("B"))
  addOutPort(new Out(("输出".in(Zh) | "Output".in(En)).str()) {
    override def getData: Double = inA.getData + inB.getData
  })

  inA.defaultData = 0.0
  inB.defaultData = 0.0

  override val name: String = ("加法".in(Zh) | "Addition".in(En)).str()
}
