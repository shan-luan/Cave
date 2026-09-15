package com.lomekwi.cave.pipeline

//TODO:WIP
@SerialVersionUID(1L)
class NodeGraphFilter extends Filter[Object] {
  private final val innerSink: Sink = new Sink()
  private final val innerNodes: NodeGraph = new NodeGraph()

  innerNodes.add(innerSink)

  addInPort(new FilterIn())
  addInPort(new Node.InPort[NodeGraph]("节点图", innerNodes, classOf[NodeGraph]))
  addOutPort(new FilterOut {
    override def getData: Object = {
      innerSink.get()
    }
  })

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
