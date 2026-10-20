package com.lomekwi.cave.pipeline

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

/**
 * 节点图的入口节点，把图外连入上游的帧提供给图内节点。
 *
 * @param upstream 宿主 filter 的输入端口，即节点图外部连入的帧
 */
@SerialVersionUID(1L)
final class GraphInNode(upstream: Node.InPort[Object]) extends Node {
  val out: Node.OutPort[Object] = addOutPort(new Node.OutPort[Object](("输出".in(Zh) | "Output".in(En)).str(), classOf[Object]) {
    override def getData: Object = {
      upstream.getData
    }

    /**
     * 上游未连接时类型未知，返回 null 让任意输入端口都能接入。
     */
    override def getType: Class[? <: Object] = {
      val prev: Node.OutPort[?] = upstream.prev
      if (prev == null) null else prev.getType.asInstanceOf[Class[? <: Object]]
    }
  })

  override val name: String = ("输入".in(Zh) | "Input".in(En)).str()

  override def canRemove: Boolean = false
}
