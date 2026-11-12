package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.FilterChainTest.{AddFilter, FpCont}
import com.lomekwi.cave.project.{IdSource, TestIds}
import com.lomekwi.cave.timeline.{Segment, Timespan}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.util.Using

/**
 * 验证 [[NodeGraphFilter]] 的图边界，入口节点把图外连入的帧送给图内节点，
 * 总输出节点把图内的结果送出图外。
 */
class NodeGraphFilterTest extends munit.FunSuite {
  given IdSource = TestIds

  test("新节点图包含边界节点") {
    val ngf = NodeGraphFilter()
    val nodes = ngf.innerNodes.toSeq

    assertEquals(nodes.size, 2)
    assertEquals(nodes.count(_.isInstanceOf[GraphInNode]), 1)
    assertEquals(nodes.count(_.isInstanceOf[Sink]), 1)
  }

  test("新节点图连接输入到汇点") {
    val ngf = NodeGraphFilter()
    assert(ngf.innerNodes.next(graphIn(ngf).out).contains(sink(ngf).in))
    assert((ngf.innerNodes.prev(sink(ngf).in)) eq (graphIn(ngf).out))
  }

  test("图输入类型跟随上游") {
    val ngf = NodeGraphFilter()
    assert((graphIn(ngf).out.getType(null)) == null)

    val segment: Segment = new FpCont(10).addFilter(ngf)
    assert((classOf[FilterChainTest.Fpable]) eq (graphIn(ngf).out.getType(segment.filterChain)))
  }

  test("图输入把帧转发给内部滤镜") {
    val add = new AddFilter(5.0)
    val base = NodeGraphFilter()
    val sinkIn = sink(base).inPorts(0).asInstanceOf[Node.InPort[Object]]
    val edited = base.innerNodes
      .add(add)
      .link(add.filterIn, graphIn(base).out)
      .link(sinkIn, add.filterOut)
    val ngf = base.withInnerNodes(edited)
    val segment: Segment = new FpCont(10).addFilter(ngf)

    assertEqualsDouble(segment.get(Timespan.Zero, null).asInstanceOf[FilterChainTest.Fpable].`val`, 15.0, 0)
  }

  test("序列化往返保留图输入") {
    val base = NodeGraphFilter()
    val add = new AddFilter(5.0)
    val sinkIn = sink(base).inPorts(0).asInstanceOf[Node.InPort[Object]]
    val edited = base.innerNodes
      .add(add)
      .link(add.filterIn, graphIn(base).out)
      .link(sinkIn, add.filterOut)
    val ngf = base.withInnerNodes(edited)
    val segment: Segment = new FpCont(10).addFilter(ngf)

    val copy: Segment = roundTrip(segment)

    val copyNgf = copy.filters(0).asInstanceOf[NodeGraphFilter]
    assert((graphIn(copyNgf)) != null)
    assertEqualsDouble(copy.get(Timespan.Zero, null).asInstanceOf[FilterChainTest.Fpable].`val`, 15.0, 0)
  }

  private def graphIn(ngf: NodeGraphFilter): GraphInNode = {
    ngf.innerNodes.collectFirst { case node: GraphInNode => node }.orNull
  }

  private def sink(ngf: NodeGraphFilter): Sink = {
    ngf.innerNodes.collectFirst { case node: Sink => node }.orNull
  }

  private def roundTrip[T](o: T): T = {
    val bos = new ByteArrayOutputStream()
    Using.resource(new ObjectOutputStream(bos)) { oos =>
      oos.writeObject(o)
    }
    Using.resource(new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) { ois =>
      ois.readObject().asInstanceOf[T]
    }
  }
}
