package com.lomekwi.cave.ui.node

import com.lomekwi.cave.pipeline.Node

trait PortRow extends PortHolder {
  override def port: Node.OutPort[?]
}
