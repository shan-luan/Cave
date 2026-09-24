package com.lomekwi.cave.pipeline

import java.io.Serializable
import java.util

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

/**
 * 过滤器节点，单一 FilterIn/FilterOut，可挂载到某个 {@link Source} 的 filter 链上。
 *
 * @author shan_luan_
 */
@SerialVersionUID(1L)
abstract class Filter[T](using protected val classTag: ClassTag[T]) extends Node with Serializable {

  private var filterIn: FilterIn = uninitialized
  private var filterOut: FilterOut = uninitialized

  override protected def addInPort[P <: Node.InPort[?]](p: P): P = {
    val port = super.addInPort(p)
    p match {
      case in: FilterIn =>
        if (filterIn != null) {
          throw new IllegalStateException("只能有一个过滤输入端口.")
        }
        filterIn = in
      case _ =>
    }
    port
  }

  override protected def addOutPort[P <: Node.OutPort[?]](p: P): P = {
    val port = super.addOutPort(p)
    p match {
      case out: FilterOut =>
        if (filterOut != null) {
          throw new IllegalStateException("只能有一个过滤输出端口.")
        }
        filterOut = out
      case _ =>
    }
    port
  }
  final def getType: Class[T] = classTag.runtimeClass.asInstanceOf[Class[T]]

  def getFilterIn: FilterIn = {
    filterIn
  }
  def getFilterOut: FilterOut = {
    filterOut
  }

  class FilterIn(name: String) extends Node.InPort[T](name, Filter.this.getType) {
    def this() = {
      this("输入")
    }

    override def getConstraint: util.Set[Class[?]] = {
      if (getFilterOut.isLinked) {
        (getFilterOut.getNext.asScala.flatMap(_.getConstraint.asScala).union(Set(Filter.this.getType))).asJava
      } else {
        util.Set.of(Filter.this.getType)
      }
    }
  }
  abstract class FilterOut(name: String) extends Node.OutPort[T](name, Filter.this.getType) {
    protected def this() = {
      this("输出")
    }

    /**
     * @return 输入已连接时返回上游实际类型,否则返回 null 表示类型未知.
     */
    override def getType: Class[? <: T] = {
      if (getFilterIn.isLinked) {
        getFilterIn.getPrev.getType
      } else {
        null
      }
    }
  }
}
