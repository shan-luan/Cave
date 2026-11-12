package com.lomekwi.cave.pipeline

import com.lomekwi.cave.project.{Id, IdSource}

@SerialVersionUID(1L)
final case class NodeGraph(nodes: Map[Id, Node], edges: Map[(Id, Int), (Id, Int)], defaults: Map[(Id, Int), Any]) extends Iterable[Node] {
  @transient @volatile private var portKeysCache: Map[Node.Port, (Id, Int, Boolean)] = null
  @transient @volatile private var nodeIdsCache: Map[Node, Id] = null
  @transient @volatile private var nextIndexCache: Map[(Id, Int), Vector[Node.InPort[?]]] = null

  def add(node: Node)(using ids: IdSource): NodeGraph = {
    if (contains(node)) this else copy(nodes = nodes.updated(ids.nextId(), node))
  }

  def remove(node: Node): NodeGraph = {
    val id = idOf(node)
    if (id == Id.noId()) {
      this
    } else {
      copy(
        nodes = nodes.removed(id),
        edges = edges.filter { case ((inId, _), (outId, _)) => inId != id && outId != id },
        defaults = defaults.filter { case ((nodeId, _), _) => nodeId != id })
    }
  }

  def idOf(node: Node): Id = {
    nodeIds.getOrElse(node, Id.noId())
  }

  def node(id: Id): Node = nodes(id)

  /** 用同 id 的新节点值替换，端口序号不变，边无需重连。 */
  def updateNode(id: Id)(f: Node => Node): NodeGraph = {
    nodes.get(id) match {
      case Some(current) => copy(nodes = nodes.updated(id, f(current)))
      case None => this
    }
  }

  def defaultOf(port: Node.InPort[?]): Any = {
    inKey(port) match {
      case Some(key) => defaults.getOrElse(key, port.defaultData)
      case None => port.defaultData
    }
  }

  def setDefault(port: Node.InPort[?], value: Any): NodeGraph = {
    inKey(port) match {
      case Some(key) => setDefault(key._1, key._2, value)
      case None => this
    }
  }

  def setDefault(id: Id, portIndex: Int, value: Any): NodeGraph = {
    copy(defaults = defaults.updated((id, portIndex), value))
  }

  def link(in: Node.InPort[?], out: Node.OutPort[?]): NodeGraph = {
    (inKey(in), outKey(out)) match {
      case (Some(inKey), Some(outKey)) => if (in.canLinkFrom(out, this)) copy(edges = edges.updated(inKey, outKey)) else this
      case _ => this
    }
  }

  def unlink(in: Node.InPort[?]): NodeGraph = {
    inKey(in) match {
      case Some(key) => copy(edges = edges.removed(key))
      case None => this
    }
  }

  def prev(in: Node.InPort[?]): Node.OutPort[?] = {
    inKey(in).flatMap(edges.get).map { case (outId, outIndex) => nodes(outId).outPorts(outIndex) }.orNull
  }

  def next(out: Node.OutPort[?]): Vector[Node.InPort[?]] = {
    outKey(out) match {
      case Some(key) => nextIndex.getOrElse(key, Vector.empty)
      case None => Vector.empty
    }
  }

  def contains(node: Node): Boolean = idOf(node) != Id.noId()

  override def size: Int = nodes.size

  override def iterator: Iterator[Node] = nodes.valuesIterator

  def clear: NodeGraph = NodeGraph.empty

  private def inKey(in: Node.InPort[?]): Option[(Id, Int)] = {
    portKeys.get(in).collect { case (id, index, true) => (id, index) }
  }

  private def outKey(out: Node.OutPort[?]): Option[(Id, Int)] = {
    portKeys.get(out).collect { case (id, index, false) => (id, index) }
  }

  private def inPortAt(key: (Id, Int)): Node.InPort[?] = nodes(key._1).inPorts(key._2)

  private def portKeys: Map[Node.Port, (Id, Int, Boolean)] = {
    val cached = portKeysCache
    if (cached != null) {
      cached
    } else {
      synchronized {
        if (portKeysCache == null) {
          portKeysCache = nodes.iterator.flatMap { case (id, node) =>
            node.inPorts.iterator.zipWithIndex.map { case (port, index) => (port: Node.Port) -> (id, index, true) } ++
              node.outPorts.iterator.zipWithIndex.map { case (port, index) => (port: Node.Port) -> (id, index, false) }
          }.toMap
        }
        portKeysCache
      }
    }
  }

  private def nodeIds: Map[Node, Id] = {
    val cached = nodeIdsCache
    if (cached != null) {
      cached
    } else {
      synchronized {
        if (nodeIdsCache == null) {
          nodeIdsCache = nodes.iterator.map { case (id, node) => node -> id }.toMap
        }
        nodeIdsCache
      }
    }
  }

  private def nextIndex: Map[(Id, Int), Vector[Node.InPort[?]]] = {
    val cached = nextIndexCache
    if (cached != null) {
      cached
    } else {
      synchronized {
        if (nextIndexCache == null) {
          nextIndexCache = edges.iterator
            .map { case (inKey, outKey) => outKey -> inPortAt(inKey) }
            .toVector
            .groupMap(_._1)(_._2)
        }
        nextIndexCache
      }
    }
  }
}

object NodeGraph {
  val empty: NodeGraph = NodeGraph(Map.empty, Map.empty, Map.empty)
}
