package com.lomekwi.cave.pipeline

import java.io.Serializable
import com.badlogic.gdx.math.Vector2

import scala.collection.mutable

@SerialVersionUID(1L)
class NodeGraph extends Iterable[Node] with Serializable {
  private val delegate: mutable.LinkedHashSet[Node] = mutable.LinkedHashSet.empty
  private val node2pos: mutable.HashMap[Node, Vector2] = mutable.HashMap.empty

  def add(node: Node): Boolean = {
    if (delegate.add(node)) {
      node2pos.put(node, new Vector2())
      true
    } else {
      false
    }
  }

  def remove(o: Any): Boolean = {
    o match {
      case node: Node if delegate.remove(node) =>
        node2pos.remove(node)
        node.remove()
        true
      case _ => false
    }
  }

  def contains(node: Node): Boolean = delegate.contains(node)

  override def size: Int = delegate.size

  override def iterator: Iterator[Node] = delegate.iterator

  def clear(): Unit = {
    delegate.clear()
    node2pos.clear()
  }

  def getPosition(node: Node): Vector2 = node2pos.getOrElse(node, null)

  def setPosition(node: Node, x: Float, y: Float): Unit = {
    val position = node2pos.getOrElse(node, null)
    require(position != null, "节点不在节点图中")
    position.set(x, y)
  }
}
