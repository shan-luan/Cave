package com.lomekwi.cave.pipeline

import java.io.Serializable

//TODO：字段全 val 化。运行时状态抽到 Runtime，资源 setter 改成 withX 回写容器。
@SerialVersionUID(1L)
abstract class Node extends Serializable {
  private var _inPorts: Vector[Node.InPort[?]] = Vector.empty
  private var _outPorts: Vector[Node.OutPort[?]] = Vector.empty

  def inPorts: Vector[Node.InPort[?]] = _inPorts

  def outPorts: Vector[Node.OutPort[?]] = _outPorts

  /**
   * 是否允许从节点图中移除。
   */
  def canRemove: Boolean = true

  protected def addInPort[P <: Node.InPort[?]](p: P): P = {
    _inPorts :+= p
    p
  }
  protected def addOutPort[P <: Node.OutPort[?]](p: P): P = {
    _outPorts :+= p
    p
  }
  def name: String
}

object Node {
  sealed trait Port extends Serializable {
    def name: String = toString
  }

  @SerialVersionUID(1L)
  class InPort[T](override val name: String, val defaultData: T, constraints: Class[?]*) extends Port {
    private final val constraintSet: Set[Class[?]] = constraints.toSet

    def this(name: String, constraints: Class[?]*) = this(name, null.asInstanceOf[T], constraints*)

    private[pipeline] def prev(context: FilterChain | NodeGraph): Node.OutPort[?] = context match {
      case chain: FilterChain => chain.prev(this)
      case graph: NodeGraph => graph.prev(this)
      case _ => null
    }

    def getData(context: FilterChain | NodeGraph): T = {
      val up = prev(context)
      if (up != null) {
        up.getData(context).asInstanceOf[T]
      } else if (context == null) {
        defaultData
      } else {
        val value: Any = context match {
          case chain: FilterChain => chain.defaultOf(this)
          case graph: NodeGraph => graph.defaultOf(this)
        }
        value.asInstanceOf[T]
      }
    }

    /**
     * @return 可连接到此输入端口的输出端口需满足的全部约束
     */
    def constraint(context: FilterChain | NodeGraph): Set[Class[?]] = constraintSet

    def canLinkFrom(p: Node.OutPort[?], context: FilterChain | NodeGraph): Boolean = {
      val outType = p.getType(context)
      outType == null || constraint(context).forall(c => c.isAssignableFrom(outType))
    }
  }


  @SerialVersionUID(1L)
  abstract class OutPort[T](override val name: String, portType: Class[? <: T]) extends Port {
    def getData(context: FilterChain | NodeGraph): T

    def getType(context: FilterChain | NodeGraph): Class[? <: T] = portType

    def canLinkTo(p: Node.InPort[?], context: FilterChain | NodeGraph): Boolean = p.canLinkFrom(this, context)
  }
}
