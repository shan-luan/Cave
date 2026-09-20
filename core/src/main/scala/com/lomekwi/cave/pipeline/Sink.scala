package com.lomekwi.cave.pipeline

final class Sink extends Node {
  private final val in: Node.InPort[Object] = addInPort(new Node.InPort[Object]("输入"))

  override def getName: String = {
    "总输出"
  }

  def getIn: Node.InPort[Object] = in

  def get(): Object = {
    in.getData
  }

  override def canRemove: Boolean = false
}
