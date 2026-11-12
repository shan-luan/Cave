package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.{FilterChain, Node, NodeGraph}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

@SerialVersionUID(1L)
final class DivNode extends Filter[Double] {
  private val in: FilterIn = addInPort(new FilterIn("A", 1.0))
  private val b: Node.InPort[Double] = addInPort(new Node.InPort[Double]("B", 1.0, classOf[Double]))

  private val out: FilterOut = addOutPort(new FilterOut(("输出".in(Zh) | "Output".in(En)).str()) {
    override def getData(context: FilterChain | NodeGraph): Double = filterIn.getData(context) / b.getData(context)
  })

  override val name: String = ("除法".in(Zh) | "Division".in(En)).str()
}
