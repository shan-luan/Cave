package com.lomekwi.cave.pipeline

import java.io.Serializable
import java.util

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@SerialVersionUID(1L)
abstract class Node extends Serializable {
  final val inPorts: util.List[Node.InPort[?]] = new util.ArrayList[Node.InPort[?]]()
  final val outPorts: util.List[Node.OutPort[?]] = new util.ArrayList[Node.OutPort[?]]()

  protected[pipeline] def remove(): Unit = {
    for (in <- inPorts.asScala) {
      in.unlink()
    }

    for (out <- outPorts.asScala) {
      out.unlink()
    }
  }

  /**
   * 是否允许从节点图中移除。图边界节点（图入口、总输出）不允许。
   */
  def canRemove: Boolean = true

  protected def addInPort[P <: Node.InPort[?]](p: P): P = {
    inPorts.add(p)
    p
  }
  protected def addOutPort[P <: Node.OutPort[?]](p: P): P = {
    outPorts.add(p)
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
    private final val constraintSet: util.Set[Class[?]] = util.Set.of(constraints*)

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
    def constraint: util.Set[Class[?]] = constraintSet

    def canLinkFrom(p: Node.OutPort[?]): Boolean = {
      val outType = p.getType
      outType == null || constraint.asScala.forall(c => c.isAssignableFrom(outType))
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

    final val next: util.Set[Node.InPort[? >: T]] = new util.HashSet[Node.InPort[? >: T]]()

    def getData: T

    def getType: Class[? <: T] = portType

    def canLinkTo(p: Node.InPort[?]): Boolean = p.canLinkFrom(this)

    private def linkTo(p: Node.InPort[?]): Boolean = p.asInstanceOf[Node.InPort[Any]].linkFrom(this.asInstanceOf[Node.OutPort[Any]])

    private[Node] def addNext(p: Node.InPort[?]): Unit = {
      next.add(p.asInstanceOf[Node.InPort[? >: T]])
    }

    private[Node] def removeNext(p: Node.InPort[?]): Unit = {
      next.remove(p)
    }

    def unlink(p: Node.InPort[?]): Unit = {
      if (next.contains(p)) {
        p.unlink()
      }
    }

    override def unlink(): Unit = {
      for (p <- util.Set.copyOf(next).asScala) {
        p.unlink()
      }

      next.clear()
    }

    def isLinked: Boolean = !next.isEmpty
  }
}
