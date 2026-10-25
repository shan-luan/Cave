package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

@SerialVersionUID(1L)
final class DivNode extends Filter[Double] {
  private final val in: FilterIn = addInPort(new FilterIn("A"))
  private final val b: Node.InPort[Double] = addInPort(new Node.InPort[Double]("B", 1.0, classOf[Double]))

  private final val out: FilterOut = addOutPort(new FilterOut(("输出".in(Zh) | "Output".in(En)).str()) {
    override def getData: Double = filterIn.getData / b.getData
  })

  filterIn.defaultData = 1.0

  override val name: String = ("除法".in(Zh) | "Division".in(En)).str()
}
