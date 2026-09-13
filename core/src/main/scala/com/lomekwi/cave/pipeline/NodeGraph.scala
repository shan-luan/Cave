package com.lomekwi.cave.pipeline

import java.io.Serializable
import java.util.{AbstractSet, Collection, HashSet, Iterator, Set}

//TODO:WIP
@SerialVersionUID(1L)
class NodeGraph extends AbstractSet[Node] with Serializable {
  private final val delegate: Set[Node] = new HashSet[Node]()

  def this(nodes: Collection[? <: Node]) = {
    this()
    delegate.addAll(nodes)
  }

  override def iterator(): Iterator[Node] = {
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
