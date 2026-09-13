package com.lomekwi.cave.pipeline

import java.io.Serializable
import java.util.{ArrayList, HashSet, List, Set}

import scala.jdk.CollectionConverters.*

/**
 * @author shan_luan_
 */
@SerialVersionUID(1L)
abstract class Node extends Serializable {
  private final val inPorts: List[Node.InPort[?]] = new ArrayList[Node.InPort[?]]()
  private final val outPorts: List[Node.OutPort[?]] = new ArrayList[Node.OutPort[?]]()

  protected def remove(): Unit = {
    for (in <- inPorts.asScala) {
      in.unlink()
    }

    for (out <- outPorts.asScala) {
      out.unlink()
    }
  }
  protected def addInPort[P <: Node.InPort[?]](p: P): P = {
    inPorts.add(p)
    p
  }
  protected def addOutPort[P <: Node.OutPort[?]](p: P): P = {
    outPorts.add(p)
    p
  }
  def getName(): String

  def getInPorts(): List[Node.InPort[?]] = inPorts
  def getOutPorts(): List[Node.OutPort[?]] = outPorts
}

object Node {
  sealed trait Port extends Serializable {
    def link(target: Port): Boolean
    def unlink(): Unit
    def getName(): String = toString
  }

  @SerialVersionUID(1L)
  class InPort[T](name: String, constraint: Class[?]*) extends Port {
    private final val constraintSet: Set[Class[?]] = Set.of(constraint*)

    private var defaultData: T = null.asInstanceOf[T]

    private var prev: Node.OutPort[? <: T] = null

    override def link(target: Port): Boolean = target match {
      case out: Node.OutPort[?] => this.asInstanceOf[Node.InPort[Any]].linkFrom(out.asInstanceOf[Node.OutPort[Any]])
      case _ => false
    }

    def this(name: String, defaultValue: T, constraint: Class[?]*) = {
      this(name, constraint*)
      this.defaultData = defaultValue
    }

    def getPrev(): Node.OutPort[? <: T] = prev

    private def setPrev(prev: Node.OutPort[? <: T]): Unit = {
      this.prev = prev
    }

    def getData(): T = if (prev == null) getDefaultData() else prev.getData()

    def getDefaultData(): T = defaultData

    def setDefaultData(data: T): Unit = defaultData = data

    /**
     * @return 可以连接到此输入端口的输出端口所需要满足的全部约束.即交叉类型(&).
     */
    def getConstraint(): Set[Class[?]] = constraintSet

    def canLinkFrom(p: Node.OutPort[?]): Boolean = {
      val outType = p.getType()

      if (outType == null) {
        return true
      }

      val it = getConstraint().iterator()
      while (it.hasNext) {
        val c = it.next()
        if (!c.isAssignableFrom(outType)) {
          return false
        }
      }

      true
    }

    def linkFrom(p: Node.OutPort[?]): Boolean = {
      if (!canLinkFrom(p)) {
        return false
      }

      unlink()

      setPrev(p.asInstanceOf[Node.OutPort[? <: T]])
      p.addNext(this)

      true
    }

    override def unlink(): Unit = {
      if (prev != null) {
        prev.removeNext(this)
        prev = null
      }
    }
    def isLinked(): Boolean = prev != null
    override def getName(): String = name
  }


  @SerialVersionUID(1L)
  abstract class OutPort[T](name: String, portType: Class[? <: T]) extends Port {
    override def link(target: Port): Boolean = target match {
      case in: Node.InPort[?] => linkTo(in)
      case _ => false
    }

    protected final val next: Set[Node.InPort[? >: T]] = new HashSet[Node.InPort[? >: T]]()

    def getData(): T

    def getType(): Class[? <: T] = portType

    def canLinkTo(p: Node.InPort[?]): Boolean = p.canLinkFrom(this)

    def linkTo(p: Node.InPort[?]): Boolean = p.asInstanceOf[Node.InPort[Any]].linkFrom(this.asInstanceOf[Node.OutPort[Any]])

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
      for (p <- Set.copyOf(next).asScala) {
        p.unlink()
      }

      next.clear()
    }

    def isLinked(): Boolean = !next.isEmpty

    def getNext(): Set[Node.InPort[? >: T]] = next
    override def getName(): String = name
  }
}
