package com.lomekwi.cave.ui.node

import com.kotcrab.vis.ui.widget.VisLabel
import com.lomekwi.cave.pipeline.Node

/**
 * 数值输出端口显示：只读数值行。
 */
final class FpOutputRow(port0: Node.OutPort[?]) extends VisLabel(port0.getName + ": " + port0.asInstanceOf[Node.OutPort[Double]].getData) with PortRow {
  private final val port: Node.OutPort[?] = port0

  override def getPort: Node.OutPort[?] = port
}
