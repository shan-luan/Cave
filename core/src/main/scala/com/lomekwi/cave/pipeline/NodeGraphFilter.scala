package com.lomekwi.cave.pipeline

import com.badlogic.gdx.math.Vector2
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import java.io.ObjectInputStream
import scala.compiletime.uninitialized

@SerialVersionUID(1L)
class NodeGraphFilter extends Filter[Object] {
  private final val innerSink: Sink = new Sink()
  final val innerNodes: NodeGraph = new NodeGraph()
  private var innerIn: GraphInNode = uninitialized

  innerNodes.add(innerSink)

  addInPort(new FilterIn())
  addInPort(new Node.InPort[NodeGraph](("节点图".in(Zh) | "Node Graph".in(En)).str(), innerNodes, classOf[NodeGraph]))

  ensureInnerIn()

  innerSink.in.linkFrom(innerIn.out)

  addOutPort(new FilterOut {
    override def getData: Object = {
      innerSink.get()
    }
  })

  private def readObject(in: ObjectInputStream): Unit = {
    in.defaultReadObject()
    ensureInnerIn()
  }

  /**
   * 创建图入口节点。
   */
  private def ensureInnerIn(): Unit = {
    if (innerIn != null) {
      return
    }
    val node: GraphInNode = new GraphInNode(filterIn)
    innerIn = node
    innerNodes.add(node)
    innerNodes.setPosition(node, 0f, 0f)
    val sinkPos: Vector2 = innerNodes.getPosition(innerSink)
    if (sinkPos.x == 0f && sinkPos.y == 0f) {
      innerNodes.setPosition(innerSink, NodeGraphFilter.SINK_OFFSET_X, 0f)
    }
  }

  override val name: String = ("节点图".in(Zh) | "Node Graph".in(En)).str()
}

object NodeGraphFilter {
  /** 新建节点图中总输出节点相对入口节点的水平偏移 */
  private val SINK_OFFSET_X: Float = 240f
}
