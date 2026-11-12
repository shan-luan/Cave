package com.lomekwi.cave.pipeline

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

/**
 * 节点图的入口节点，把图外连入上游的帧提供给图内节点。
 *
 * @param upstream 宿主 filter 的输入端口，即节点图外部连入的帧
 */
@SerialVersionUID(1L)
final class GraphInNode(upstream: Node.InPort[Object]) extends Node {
  @transient private var input: Object = null
  @transient private var inputType: Class[?] = null

  def bind(value: Object, `type`: Class[?]): Unit = {
    input = value
    inputType = `type`
  }

  val out: Node.OutPort[Object] = addOutPort(new Node.OutPort[Object](("输出".in(Zh) | "Output".in(En)).str(), classOf[Object]) {
    override def getData(context: FilterChain | NodeGraph): Object = context match {
      case chain: FilterChain => upstream.getData(chain)
      case _ => input
    }

    /**
     * 上游未连接时类型未知，返回 null 让任意输入端口都能接入。
     */
    override def getType(context: FilterChain | NodeGraph): Class[? <: Object] = context match {
      case chain: FilterChain =>
        val up: Node.OutPort[?] = upstream.prev(chain)
        if (up == null) null else up.getType(chain).asInstanceOf[Class[? <: Object]]
      case _ => inputType
    }
  })

  override val name: String = ("输入".in(Zh) | "Input".in(En)).str()

  override def canRemove: Boolean = false
}
