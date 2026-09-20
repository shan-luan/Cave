package com.lomekwi.cave.pipeline

import com.badlogic.gdx.math.Vector2

import java.io.ObjectInputStream

//TODO:WIP
@SerialVersionUID(1L)
class NodeGraphFilter extends Filter[Object] {
  private final val innerSink: Sink = new Sink()
  private final val innerNodes: NodeGraph = new NodeGraph()
  private var innerIn: GraphInNode = null

  innerNodes.add(innerSink)

  addInPort(new FilterIn())
  addInPort(new Node.InPort[NodeGraph]("节点图", innerNodes, classOf[NodeGraph]))

  ensureInnerIn()

  // 只在新图里默认连线。反序列化补建入口节点时不连，避免覆盖旧存档中用户已有的连接。
  innerSink.getIn.linkFrom(innerIn.getOut)

  addOutPort(new FilterOut {
    override def getData: Object = {
      innerSink.get()
    }
  })

  /** 反序列化的旧存档里没有入口节点（构造器不执行），取图时补建，避免图里缺边界。 */
  private def readObject(in: ObjectInputStream): Unit = {
    in.defaultReadObject()
    ensureInnerIn()
  }

  /**
   * 创建图入口节点。入口依赖自身的 FilterIn，因此必须在 addInPort 之后调用。
   */
  private def ensureInnerIn(): Unit = {
    if (innerIn != null) {
      return
    }
    val node: GraphInNode = new GraphInNode(getFilterIn)
    innerIn = node
    innerNodes.add(node)
    innerNodes.setPosition(node, 0f, 0f)
    // 两个边界节点都停在默认位置时（旧存档），按初始布局把它们摆开
    val sinkPos: Vector2 = innerNodes.getPosition(innerSink)
    if (sinkPos.x == 0f && sinkPos.y == 0f) {
      innerNodes.setPosition(innerSink, NodeGraphFilter.SINK_OFFSET_X, 0f)
    }
  }

  def getInnerNodes: NodeGraph = {
    innerNodes
  }

  override def getName: String = {
    "节点图"
  }

  override def getType: Class[Object] = {
    classOf[Object]
  }
}

object NodeGraphFilter {
  /** 新建节点图中总输出节点相对入口节点的水平偏移 */
  private final val SINK_OFFSET_X: Float = 240f
}
