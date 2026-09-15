package com.lomekwi.cave.pipeline

import java.io.Serializable
import java.util

//TODO:WIP
@SerialVersionUID(1L)
class NodeGraph extends util.AbstractSet[Node] with Serializable {
  private final val delegate: util.Set[Node] = new util.HashSet[Node]()

  def this(nodes: util.Collection[? <: Node]) = {
    this()
    delegate.addAll(nodes)
  }

  override def iterator(): util.Iterator[Node] = {
    delegate.iterator()
  }

  override def size(): Int = {
    delegate.size()
  }

  override def contains(o: Any): Boolean = {
    delegate.contains(o)
  }

  override def add(node: Node): Boolean = {
    delegate.add(node)
  }

  override def remove(o: Any): Boolean = {
    delegate.remove(o)
  }

  override def clear(): Unit = {
    delegate.clear()
  }
}
