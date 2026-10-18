package com.lomekwi.cave.pipeline

import com.lomekwi.cave.util.legacy_i18n.I18N.i18n

final class Sink extends Node {
  final val in: Node.InPort[Object] = addInPort(new Node.InPort[Object](i18n("输入")))

  override val name: String = i18n("总输出")

  def get(): Object = {
    in.getData
  }

  override def canRemove: Boolean = false
}
