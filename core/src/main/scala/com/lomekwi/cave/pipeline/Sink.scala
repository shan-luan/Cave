package com.lomekwi.cave.pipeline

final class Sink extends Node {
  final val in: Node.InPort[Object] = addInPort(new Node.InPort[Object]("输入"))

  override val name: String = "总输出"

  def get(): Object = {
    in.getData
  }

  override def canRemove: Boolean = false
}
