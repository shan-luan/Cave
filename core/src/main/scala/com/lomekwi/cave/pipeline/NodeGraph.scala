package com.lomekwi.cave.pipeline

import java.io.Serializable
import java.util
import com.badlogic.gdx.math.Vector2

import scala.jdk.CollectionConverters.*

//TODO:WIP
@SerialVersionUID(1L)
class NodeGraph extends util.AbstractSet[Node] with Serializable {
  private val delegate: util.Set[Node] = new util.HashSet[Node]()
  private val node2pos: util.Map[Node,Vector2] = new util.HashMap[Node,Vector2]()

  def this(nodes: util.Collection[? <: Node]) = {
    this()
    nodes.asScala.foreach(add)
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
    if (delegate.add(node)) {
      node2pos.put(node, new Vector2())
      true
    } else {
      false
    }
  }

  override def remove(o: Any): Boolean = {
    if (delegate.remove(o)) {
      node2pos.remove(o)
      o match {
        case node: Node => node.remove()
        case _ =>
      }
      true
    } else {
      false
    }
  }

  override def clear(): Unit = {
    delegate.clear()
    node2pos.clear()
  }

  def getPosition(node: Node): Vector2 = {
    node2pos.get(node)
  }

  def setPosition(node: Node, x: Float, y: Float): Unit = {
    val position = node2pos.get(node)
    require(position != null, "节点不在节点图中")
    position.set(x, y)
  }
}
