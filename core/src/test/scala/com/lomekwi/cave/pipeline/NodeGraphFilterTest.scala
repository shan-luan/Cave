package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.FilterListTest.{AddFilter, FpCont}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotEquals, assertNotNull, assertNull, assertSame, assertTrue}
import org.junit.jupiter.api.Test

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.util.Using

/**
 * 验证 [[NodeGraphFilter]] 的图边界，入口节点把图外连入的帧送给图内节点，
 * 总输出节点把图内的结果送出图外。
 */
class NodeGraphFilterTest {

  @Test
  def newGraph_hasBoundaryNodes(): Unit = {
    val ngf = new NodeGraphFilter()
    val nodes = ngf.innerNodes.toSeq

    assertEquals(2, nodes.size)
    assertEquals(1, nodes.count(_.isInstanceOf[GraphInNode]))
    assertEquals(1, nodes.count(_.isInstanceOf[Sink]))

    val in = graphIn(ngf)
    val sink = nodes.collectFirst { case s: Sink => s }.get
    assertNotEquals(ngf.innerNodes.getPosition(in), ngf.innerNodes.getPosition(sink))
  }

  @Test
  def newGraph_connectsInputToSink(): Unit = {
    val ngf = new NodeGraphFilter()
    assertTrue(graphIn(ngf).out.isLinked)
    assertTrue(sink(ngf).in.isLinked)
  }

  @Test
  def graphInput_typeFollowsUpstream(): Unit = {
    val ngf = new NodeGraphFilter()
    assertNull(graphIn(ngf).out.getType)

    val segment = new FpCont(10)
    segment.filters += ngf
    assertSame(classOf[FilterListTest.Fpable], graphIn(ngf).out.getType)
  }

  @Test
  def graphInput_forwardsFrameToInnerFilter(): Unit = {
    val segment = new FpCont(10)
    val ngf = new NodeGraphFilter()
    segment.filters += ngf

    val add = new AddFilter()
    setDelta(add, 5)
    add.filterIn.linkFrom(graphIn(ngf).out)
    val sinkIn = sink(ngf).inPorts(0).asInstanceOf[Node.InPort[Object]]
    sinkIn.linkFrom(add.filterOut)

    assertEquals(15.0, segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 0)
  }

  @Test
  def serialization_roundTrip_keepsGraphInput(): Unit = {
    val segment = new FpCont(10)
    val ngf = new NodeGraphFilter()
    segment.filters += ngf
    val add = new AddFilter()
    setDelta(add, 5)
    add.filterIn.linkFrom(graphIn(ngf).out)
    sink(ngf).inPorts(0).asInstanceOf[Node.InPort[Object]].linkFrom(add.filterOut)

    val copy: FpCont = roundTrip(segment)

    val copyNgf = copy.filters(0).asInstanceOf[NodeGraphFilter]
    assertNotNull(graphIn(copyNgf))
    assertEquals(15.0, copy.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 0)
  }

  @Test
  def serialization_repairsMissingGraphInput(): Unit = {
    val ngf = new NodeGraphFilter()
    val field = classOf[NodeGraphFilter].getDeclaredField("innerIn")
    field.setAccessible(true)
    field.set(ngf, null)
    ngf.innerNodes.remove(graphIn(ngf))

    val copy: NodeGraphFilter = roundTrip(ngf)

    assertNotNull(graphIn(copy))
    assertEquals(2, copy.innerNodes.size)
    assertNotEquals(copy.innerNodes.getPosition(graphIn(copy)), copy.innerNodes.getPosition(sink(copy)))
    assertFalse(graphIn(copy).out.isLinked)
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
