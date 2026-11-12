package com.lomekwi.cave.pipeline

import com.lomekwi.cave.project.{IdSource, TestIds}

/**
 * 验证 [[NodeGraph]] 的增删语义。
 */
class NodeGraphTest extends munit.FunSuite {
  given IdSource = TestIds

  test("删除节点断开两侧连接") {
    var graph = NodeGraph.empty
    val out = new TestOutNode()
    val in = new TestInNode()
    graph = graph.add(out)
    graph = graph.add(in)
    graph = graph.link(in.in, out.out)

    graph = graph.remove(out)

    assert(graph.prev(in.in) == null)
    assert(graph.next(out.out).isEmpty)
    assert(graph.contains(in))
  }

  test("删除不存在的节点保持图不变") {
    val graph = NodeGraph.empty
    assert((graph.remove(new TestInNode())) eq graph)
  }

  test("canRemove 默认为 true") {
    assert(new TestInNode().canRemove)
  }

  test("边界节点不可删除") {
    val ngf = NodeGraphFilter()
    val nodes = ngf.innerNodes.toSeq
    assert(!(nodes.collectFirst { case n: GraphInNode => n }.get.canRemove))
    assert(!(nodes.collectFirst { case n: Sink => n }.get.canRemove))
  }
}

private[pipeline] final class TestOutNode extends Node {
  val out: Node.OutPort[Object] = addOutPort(new Node.OutPort[Object]("输出", classOf[Object]) {
    override def getData(context: FilterChain | NodeGraph): Object = null
  })

  override val name: String = "测试输出"
}

private[pipeline] final class TestInNode extends Node {
  val in: Node.InPort[Object] = addInPort(new Node.InPort[Object]("输入"))

  override val name: String = "测试输入"
}
