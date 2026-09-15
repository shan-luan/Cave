package com.lomekwi.cave.ui.node

import com.lomekwi.cave.pipeline.Node

trait PortEditor extends PortHolder {
  override def getPort: Node.InPort[?]
}
