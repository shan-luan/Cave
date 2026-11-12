package com.lomekwi.cave.pipeline

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

final class Sink extends Node {
  val in: Node.InPort[Object] = addInPort(new Node.InPort[Object](("输入".in(Zh) | "Input".in(En)).str()))

  override val name: String = ("总输出".in(Zh) | "Master Output".in(En)).str()

  def get(graph: NodeGraph): Object = {
    in.getData(graph)
  }

  override def canRemove: Boolean = false
}
