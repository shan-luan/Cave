package com.lomekwi.cave.pipeline

import java.io.Serializable

import scala.collection.mutable
import scala.compiletime.uninitialized

@SerialVersionUID(1L)
abstract class Node extends Serializable {
  final val inPorts: mutable.ArrayBuffer[Node.InPort[?]] = mutable.ArrayBuffer.empty
  final val outPorts: mutable.ArrayBuffer[Node.OutPort[?]] = mutable.ArrayBuffer.empty

  protected[pipeline] def remove(): Unit = {
    for (in <- inPorts) {
      in.unlink()
    }

    for (out <- outPorts) {
      out.unlink()
    }
  }

  /**
   * 是否允许从节点图中移除。图边界节点（图入口、总输出）不允许。
   */
  def canRemove: Boolean = true

  protected def addInPort[P <: Node.InPort[?]](p: P): P = {
    inPorts += p
    p
  }
  protected def addOutPort[P <: Node.OutPort[?]](p: P): P = {
    outPorts += p
    p
  }
  def name: String
}

object Node {
  sealed trait Port extends Serializable {
    def link(target: Port): Boolean
    def unlink(): Unit
    def name: String = toString
  }

  @SerialVersionUID(1L)
  class InPort[T](override val name: String, constraints: Class[?]*) extends Port {
    private final val constraintSet: Set[Class[?]] = constraints.toSet

    var defaultData: T = null.asInstanceOf[T]

    var prev: Node.OutPort[? <: T] = uninitialized

    override def link(target: Port): Boolean = target match {
      case out: Node.OutPort[?] => this.asInstanceOf[Node.InPort[Any]].linkFrom(out.asInstanceOf[Node.OutPort[Any]])
      case _ => false
    }

    def this(name: String, defaultValue: T, constraints: Class[?]*) = {
      this(name, constraints*)
      this.defaultData = defaultValue
    }

    def getData: T = if (prev == null) defaultData else prev.getData

    /**
     * @return 可连接到此输入端口的输出端口需满足的全部约束，即交叉类型（&）。
     */
    def constraint: Set[Class[?]] = constraintSet

    def canLinkFrom(p: Node.OutPort[?]): Boolean = {
      val outType = p.getType
      outType == null || constraint.forall(c => c.isAssignableFrom(outType))
    }

    def linkFrom(p: Node.OutPort[?]): Boolean = {
      if (!canLinkFrom(p)) {
        false
      } else {
        unlink()

        prev = p.asInstanceOf[Node.OutPort[? <: T]]
        p.addNext(this)

        true
      }
    }

    override def unlink(): Unit = {
      if (prev != null) {
        prev.removeNext(this)
        prev = null
      }
    }
    def isLinked: Boolean = prev != null
  }


  @SerialVersionUID(1L)
  abstract class OutPort[T](override val name: String, portType: Class[? <: T]) extends Port {
    override def link(target: Port): Boolean = target match {
      case in: Node.InPort[?] => linkTo(in)
      case _ => false
    }

    final val next: mutable.LinkedHashSet[Node.InPort[? >: T]] = mutable.LinkedHashSet.empty

    def getData: T

    def getType: Class[? <: T] = portType

    def canLinkTo(p: Node.InPort[?]): Boolean = p.canLinkFrom(this)

    private def linkTo(p: Node.InPort[?]): Boolean = p.asInstanceOf[Node.InPort[Any]].linkFrom(this.asInstanceOf[Node.OutPort[Any]])

    private[Node] def addNext(p: Node.InPort[?]): Unit = {
      next.add(p.asInstanceOf[Node.InPort[? >: T]])
    }

    private[Node] def removeNext(p: Node.InPort[?]): Unit = {
      next.remove(p.asInstanceOf[Node.InPort[? >: T]])
    }

    def unlink(p: Node.InPort[?]): Unit = {
      if (next.exists(_ eq p)) {
        p.unlink()
      }
    }

    override def unlink(): Unit = {
      for (p <- next.toVector) {
        p.unlink()
      }

      next.clear()
    }

    def isLinked: Boolean = next.nonEmpty
  }
}
