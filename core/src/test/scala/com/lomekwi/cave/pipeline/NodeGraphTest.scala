package com.lomekwi.cave.pipeline

/**
 * 验证 [[NodeGraph]] 的增删语义，移除节点时同时断开它在图内的全部连接。
 */
class NodeGraphTest extends munit.FunSuite {

  test("删除节点断开两侧连接") {
    val graph = new NodeGraph()
    val out = new TestOutNode()
    val in = new TestInNode()
    graph.add(out)
    graph.add(in)
    assert(in.in.linkFrom(out.out))

    assert(graph.remove(out))

    assert(!(in.in.isLinked))
    assert(!(out.out.isLinked))
    assert(graph.contains(in))
  }

  test("删除不存在的节点返回 false") {
    val graph = new NodeGraph()
    assert(!(graph.remove(new TestInNode())))
  }

  test("canRemove 默认为 true") {
    assert(new TestInNode().canRemove)
  }

  test("边界节点不可删除") {
    val ngf = new NodeGraphFilter()
    val nodes = ngf.innerNodes.toSeq
    assert(!(nodes.collectFirst { case n: GraphInNode => n }.get.canRemove))
    assert(!(nodes.collectFirst { case n: Sink => n }.get.canRemove))
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
