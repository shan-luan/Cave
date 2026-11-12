package com.lomekwi.cave.pipeline

import com.lomekwi.cave.project.{Id, IdSource}
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

@SerialVersionUID(1L)
class NodeGraphFilter private (
    existingGraph: NodeGraph,
    oldInnerIn: GraphInNode,
    existingSink: Sink
)(using ids: IdSource) extends Filter[Object] {

  private final val innerSink: Sink = if (existingSink == null) new Sink() else existingSink

  addInPort(new FilterIn())
  addInPort(new Node.InPort[NodeGraph](("节点图".in(Zh) | "Node Graph".in(En)).str(), classOf[NodeGraph]))

  private final val innerIn: GraphInNode = new GraphInNode(filterIn)

  val innerNodes: NodeGraph = {
    if (existingGraph == null) {
      NodeGraph.empty.add(innerSink).add(innerIn).link(innerSink.in, innerIn.out)
    } else {
      val oldId = existingGraph.idOf(oldInnerIn)
      //FIXME：找不到旧入口时会原样返回图，但 innerIn 已指向新 filterIn，求值会用错上游；应断言或兜底。
      if (oldId == Id.noId()) existingGraph else existingGraph.updateNode(oldId)(_ => innerIn)
    }
  }

  addOutPort(new FilterOut {
    override def getData(context: FilterChain | NodeGraph): Object = evalInner(context)
  })

  private def evalInner(context: FilterChain | NodeGraph): Object = {
    val up: Node.OutPort[?] = filterIn.prev(context)
    innerIn.bind(filterIn.getData(context), if (up == null) null else up.getType(context))
    innerSink.get(innerNodes)
  }

  def withInnerNodes(nextGraph: NodeGraph): NodeGraphFilter = {
    new NodeGraphFilter(nextGraph, innerIn, innerSink)(using NodeGraphFilter.NoIds)
  }

  override val name: String = ("节点图".in(Zh) | "Node Graph".in(En)).str()
}

object NodeGraphFilter {
  def apply()(using ids: IdSource): NodeGraphFilter = new NodeGraphFilter(null, null, null)(using ids)

  sealed trait Owner
  final case class InChain(segmentId: Id, filterId: Id) extends Owner
  final case class InGraph(nodeId: Id) extends Owner

  private[pipeline] object NoIds extends IdSource {
    override def nextId(): Id = Id.noId()
  }
}
