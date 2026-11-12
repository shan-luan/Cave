package com.lomekwi.cave.pipeline

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import java.io.Serializable

import scala.reflect.ClassTag

/**
 * 过滤器节点，单一 [[Filter.FilterIn]] 与 [[Filter.FilterOut]]，可挂载到某个 [[Segment]] 的 filter 链上。
 */
@SerialVersionUID(1L)
abstract class Filter[T](using protected val classTag: ClassTag[T]) extends Node with Serializable {

  def filterIn: FilterIn = inPorts.collectFirst { case in: FilterIn => in }.orNull

  def filterOut: FilterOut = outPorts.collectFirst { case out: FilterOut => out }.orNull

  override protected def addInPort[P <: Node.InPort[?]](p: P): P = {
    if (p.isInstanceOf[FilterIn] && filterIn != null) {
      throw new IllegalStateException(("只能有一个过滤输入端口.".in(Zh) | "There can only be one filter input port.".in(En)).str())
    }
    super.addInPort(p)
  }

  override protected def addOutPort[P <: Node.OutPort[?]](p: P): P = {
    if (p.isInstanceOf[FilterOut] && filterOut != null) {
      throw new IllegalStateException(("只能有一个过滤输出端口.".in(Zh) | "There can only be one filter output port.".in(En)).str())
    }
    super.addOutPort(p)
  }

  final def getType: Class[T] = classTag.runtimeClass.asInstanceOf[Class[T]]

  class FilterIn(name: String, defaultValue: T) extends Node.InPort[T](name, defaultValue, Filter.this.getType) {
    def this(name: String) = {
      this(name, null.asInstanceOf[T])
    }

    def this() = {
      this(("输入".in(Zh) | "Input".in(En)).str())
    }

    override def constraint(context: FilterChain | NodeGraph): Set[Class[?]] = {
      context match {
        case graph: NodeGraph => graph.next(filterOut).flatMap(_.constraint(context)).toSet + Filter.this.getType
        case _ => Set(Filter.this.getType)
      }
    }
  }

  abstract class FilterOut(name: String) extends Node.OutPort[T](name, Filter.this.getType) {
    protected def this() = {
      this(("输出".in(Zh) | "Output".in(En)).str())
    }

    /**
     * @return 上游实际类型
     */
    override def getType(context: FilterChain | NodeGraph): Class[? <: T] = {
      val up: Node.OutPort[?] = filterIn.prev(context)
      if (up == null) null else up.getType(context).asInstanceOf[Class[? <: T]]
    }
  }
}
