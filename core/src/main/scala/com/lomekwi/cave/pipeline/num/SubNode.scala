package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.BinaryNode
import com.lomekwi.cave.util.legacy_i18n.I18N.i18n

@SerialVersionUID(1L)
final class SubNode extends BinaryNode[Double] {
  addInPort(new In("A"))
  addInPort(new In("B"))
  addOutPort(new Out(i18n("输出")) {
    override def getData: Double = inA.getData - inB.getData
  })

  inA.defaultData = 0.0
  inB.defaultData = 0.0

  override val name: String = i18n("减法")
}
