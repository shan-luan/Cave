package com.lomekwi.cave.pipeline

import java.io.Serializable
import java.util

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag

/**
 * 二元节点，两个主输入端口 In 与一个主输出端口 Out，两个输入与输出同为帧类型 T。
 *
 * <p>二元节点有两条主输入，不参与 [[FilterList]] 的单向链，只用于节点图内部。</p>
 *
 * @author shan_luan_
 */
@SerialVersionUID(1L)
abstract class BinaryNode[T](using protected val classTag: ClassTag[T]) extends Node with Serializable {

  private var inA: In = uninitialized
  private var inB: In = uninitialized
  private var out: Out = uninitialized

  override protected def addInPort[P <: Node.InPort[?]](p: P): P = {
    val port = super.addInPort(p)
    p match {
      case in: In =>
        if (inA == null) {
          inA = in
        } else if (inB == null) {
          inB = in
        } else {
          throw new IllegalStateException("只能有两个输入端口.")
        }
      case _ =>
    }
    port
  }

  override protected def addOutPort[P <: Node.OutPort[?]](p: P): P = {
    val port = super.addOutPort(p)
    p match {
      case o: Out =>
        if (out != null) {
          throw new IllegalStateException("只能有一个输出端口.")
        }
        out = o
      case _ =>
    }
    port
  }

  final def getType: Class[T] = classTag.runtimeClass.asInstanceOf[Class[T]]

  def getInA: In = inA

  def getInB: In = inB

  def getOut: Out = out

  class In(name: String) extends Node.InPort[T](name, BinaryNode.this.getType) {
    /**
     * 两个输入共用同一输出，因此各自把输出下游的约束并入自身约束。
     */
    override def getConstraint: util.Set[Class[?]] = {
      if (getOut.isLinked) {
        (getOut.getNext.asScala.flatMap(_.getConstraint.asScala).union(Set(BinaryNode.this.getType))).asJava
      } else {
        util.Set.of(BinaryNode.this.getType)
      }
    }
  }

  abstract class Out(name: String) extends Node.OutPort[T](name, BinaryNode.this.getType) {
    protected def this() = {
      this("输出")
    }

    /**
     * 两个输入都未连接时类型未知，返回 null 让任意输入端口都能接入；
     * 否则取 A 的实际上游类型，A 未连接时退回 B。
     */
    override def getType: Class[? <: T] = {
      val prev: Node.OutPort[? <: T] = linkedUpstream
      if (prev == null) null else prev.getType
    }

    private def linkedUpstream: Node.OutPort[? <: T] = {
      if (getInA.isLinked) {
        getInA.getPrev
      } else if (getInB.isLinked) {
        getInB.getPrev
      } else {
        null
      }
    }
  }
}
