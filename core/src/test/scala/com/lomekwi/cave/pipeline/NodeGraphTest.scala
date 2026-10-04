package com.lomekwi.cave.pipeline

import org.junit.jupiter.api.Assertions.{assertFalse, assertTrue}
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

/**
 * 验证 [[NodeGraph]] 的增删语义，移除节点时同时断开它在图内的全部连接。
 */
class NodeGraphTest {

  @Test
  def remove_disconnectsBothSides(): Unit = {
    val graph = new NodeGraph()
    val out = new TestOutNode()
    val in = new TestInNode()
    graph.add(out)
    graph.add(in)
    assertTrue(in.in.linkFrom(out.out))

    assertTrue(graph.remove(out))

    assertFalse(in.in.isLinked)
    assertFalse(out.out.isLinked)
    assertTrue(graph.contains(in))
  }

  @Test
  def remove_absentNodeReturnsFalse(): Unit = {
    val graph = new NodeGraph()
    assertFalse(graph.remove(new TestInNode()))
  }

  @Test
  def canRemove_defaultsToTrue(): Unit = {
    assertTrue(new TestInNode().canRemove)
  }

  @Test
  def boundaryNodesAreNotRemovable(): Unit = {
    val ngf = new NodeGraphFilter()
    val nodes = ngf.innerNodes.asScala.toSeq
    assertFalse(nodes.collectFirst { case n: GraphInNode => n }.get.canRemove)
    assertFalse(nodes.collectFirst { case n: Sink => n }.get.canRemove)
  }
}

private[pipeline] final class TestOutNode extends Node {
  final val out: Node.OutPort[Object] = addOutPort(new Node.OutPort[Object]("输出", classOf[Object]) {
    override def getData: Object = null
  })

  override val name: String = "测试输出"
}

private[pipeline] final class TestInNode extends Node {
  final val in: Node.InPort[Object] = addInPort(new Node.InPort[Object]("输入"))

  override val name: String = "测试输入"
}
