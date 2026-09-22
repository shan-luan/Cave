package com.lomekwi.cave.pipeline

import java.io.Serializable
import java.util

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

/**
 * 混合器节点：两个主输入端口 MixIn 与一个主输出端口 MixOut，两个输入与输出同为帧类型 T。
 *
 * <p>混合器有两条主输入，不参与 {@link FilterList} 的单向链，只用于节点图内部。</p>
 *
 * @author shan_luan_
 */
@SerialVersionUID(1L)
abstract class Mixer[T] extends Node with Serializable {

  private var mixInA: MixIn = uninitialized
  private var mixInB: MixIn = uninitialized
  private var mixOut: MixOut = uninitialized

  override protected def addInPort[P <: Node.InPort[?]](p: P): P = {
    val port = super.addInPort(p)
    p match {
      case in: MixIn =>
        if (mixInA == null) {
          mixInA = in
        } else if (mixInB == null) {
          mixInB = in
        } else {
          throw new IllegalStateException("只能有两个混合输入端口.")
        }
      case _ =>
    }
    port
  }

  override protected def addOutPort[P <: Node.OutPort[?]](p: P): P = {
    val port = super.addOutPort(p)
    p match {
      case out: MixOut =>
        if (mixOut != null) {
          throw new IllegalStateException("只能有一个混合输出端口.")
        }
        mixOut = out
      case _ =>
    }
    port
  }

  def getType: Class[T]

  def getMixInA: MixIn = mixInA

  def getMixInB: MixIn = mixInB

  def getMixOut: MixOut = mixOut

  class MixIn(name: String) extends Node.InPort[T](name, Mixer.this.getType) {
    /**
     * 两个输入共用同一输出，因此各自把输出下游的约束并入自身约束。
     */
    override def getConstraint: util.Set[Class[?]] = {
      if (getMixOut.isLinked) {
        (getMixOut.getNext.asScala.flatMap(_.getConstraint.asScala).union(Set(Mixer.this.getType))).asJava
      } else {
        util.Set.of(Mixer.this.getType)
      }
    }
  }

  abstract class MixOut(name: String) extends Node.OutPort[T](name, Mixer.this.getType) {
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
      if (getMixInA.isLinked) {
        getMixInA.getPrev
      } else if (getMixInB.isLinked) {
        getMixInB.getPrev
      } else {
        null
      }
    }
  }
}
