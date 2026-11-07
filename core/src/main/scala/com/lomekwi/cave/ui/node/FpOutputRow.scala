package com.lomekwi.cave.ui.node

import com.kotcrab.vis.ui.widget.VisLabel
import com.lomekwi.cave.pipeline.{EvalClock, Node}

/**
 * 输出端口的只读显示行。
 */
final class FpOutputRow(port0: Node.OutPort[?]) extends VisLabel(port0.name + ": " + EvalClock.withTime(0L, 0L)(port0.asInstanceOf[Node.OutPort[Double]].getData)) with PortRow {
  override val port: Node.OutPort[?] = port0
}
