package com.lomekwi.cave.ui.node

import com.lomekwi.cave.pipeline.Node

trait PortHolder {
  def getPort(): Node.Port
}
