package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.FilterListTest.{AddFilter, FpCont}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.util.Using

/**
 * 验证 [[NodeGraphFilter]] 的图边界，入口节点把图外连入的帧送给图内节点，
 * 总输出节点把图内的结果送出图外。
 */
class NodeGraphFilterTest extends munit.FunSuite {

  test("新节点图包含边界节点") {
    val ngf = new NodeGraphFilter()
    val nodes = ngf.innerNodes.toSeq

    assertEquals(nodes.size, 2)
    assertEquals(nodes.count(_.isInstanceOf[GraphInNode]), 1)
    assertEquals(nodes.count(_.isInstanceOf[Sink]), 1)

    val in = graphIn(ngf)
    val sink = nodes.collectFirst { case s: Sink => s }.get
    assertNotEquals(ngf.innerNodes.getPosition(in), ngf.innerNodes.getPosition(sink))
  }

  test("新节点图连接输入到汇点") {
    val ngf = new NodeGraphFilter()
    assert(graphIn(ngf).out.isLinked)
    assert(sink(ngf).in.isLinked)
  }

  test("图输入类型跟随上游") {
    val ngf = new NodeGraphFilter()
    assert((graphIn(ngf).out.getType) == null)

    val segment = new FpCont(10)
    segment.filters += ngf
    assert((classOf[FilterListTest.Fpable]) eq (graphIn(ngf).out.getType))
  }

  test("图输入把帧转发给内部滤镜") {
    val segment = new FpCont(10)
    val ngf = new NodeGraphFilter()
    segment.filters += ngf

    val add = new AddFilter()
    setDelta(add, 5)
    add.filterIn.linkFrom(graphIn(ngf).out)
    val sinkIn = sink(ngf).inPorts(0).asInstanceOf[Node.InPort[Object]]
    sinkIn.linkFrom(add.filterOut)

    assertEqualsDouble(segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 15.0, 0)
  }

  test("序列化往返保留图输入") {
    val segment = new FpCont(10)
    val ngf = new NodeGraphFilter()
    segment.filters += ngf
    val add = new AddFilter()
    setDelta(add, 5)
    add.filterIn.linkFrom(graphIn(ngf).out)
    sink(ngf).inPorts(0).asInstanceOf[Node.InPort[Object]].linkFrom(add.filterOut)

    val copy: FpCont = roundTrip(segment)

    val copyNgf = copy.filters(0).asInstanceOf[NodeGraphFilter]
    assert((graphIn(copyNgf)) != null)
    assertEqualsDouble(copy.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 15.0, 0)
  }

  test("序列化修复缺失的图输入") {
    val ngf = new NodeGraphFilter()
    val field = classOf[NodeGraphFilter].getDeclaredField("innerIn")
    field.setAccessible(true)
    field.set(ngf, null)
    ngf.innerNodes.remove(graphIn(ngf))

    val copy: NodeGraphFilter = roundTrip(ngf)

    assert((graphIn(copy)) != null)
    assertEquals(copy.innerNodes.size, 2)
    assertNotEquals(copy.innerNodes.getPosition(graphIn(copy)), copy.innerNodes.getPosition(sink(copy)))
    assert(!(graphIn(copy).out.isLinked))
  }

  private def graphIn(ngf: NodeGraphFilter): GraphInNode = {
    ngf.innerNodes.collectFirst { case node: GraphInNode => node }.orNull
  }

  private def sink(ngf: NodeGraphFilter): Sink = {
    ngf.innerNodes.collectFirst { case node: Sink => node }.orNull
  }

  /** [[AddFilter.delta]] 端口是私有的，按端口名取值设置默认数据。 */
  private def setDelta(add: AddFilter, value: Double): Unit = {
    add.inPorts.find(_.name == "delta").get.asInstanceOf[Node.InPort[Any]].defaultData = value
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
