package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.FilterListTest.{AddFilter, FpCont}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotEquals, assertNotNull, assertNull, assertSame, assertTrue}
import org.junit.jupiter.api.Test

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * 验证 [[NodeGraphFilter]] 的图边界，入口节点把图外连入的帧送给图内节点，
 * 总输出节点把图内的结果送出图外。
 *
 * 覆盖如下。
 * 1) 新建即含入口节点与总输出节点，且两者位置不重叠；
 * 2) 入口节点向上游未连接时类型未知（null），连接后为上游实际类型；
 * 3) 端到端求值，图外源 → 图入口 → 图内 filter → 总输出 → 图外；
 * 4) 序列化往返后上述行为不变（入口节点反向引用宿主 filter）。
 */
class NodeGraphFilterTest {

  @Test
  def newGraph_hasBoundaryNodes(): Unit = {
    val ngf = new NodeGraphFilter()
    val nodes = ngf.getInnerNodes.asScala.toSeq

    assertEquals(2, nodes.size)
    assertEquals(1, nodes.count(_.isInstanceOf[GraphInNode]))
    assertEquals(1, nodes.count(_.isInstanceOf[Sink]))

    val in = graphIn(ngf)
    val sink = nodes.collectFirst { case s: Sink => s }.get
    assertNotEquals(ngf.getInnerNodes.getPosition(in), ngf.getInnerNodes.getPosition(sink))
  }

  @Test
  def newGraph_connectsInputToSink(): Unit = {
    val ngf = new NodeGraphFilter()
    assertTrue(graphIn(ngf).getOut.isLinked)
    assertTrue(sink(ngf).getIn.isLinked)
  }

  @Test
  def graphInput_typeFollowsUpstream(): Unit = {
    val ngf = new NodeGraphFilter()
    assertNull(graphIn(ngf).getOut.getType)

    val src = new FpCont(10)
    src.getFilters.add(ngf)
    assertSame(classOf[FilterListTest.Fpable], graphIn(ngf).getOut.getType)
  }

  @Test
  def graphInput_forwardsFrameToInnerFilter(): Unit = {
    val src = new FpCont(10)
    val ngf = new NodeGraphFilter()
    src.getFilters.add(ngf)

    val add = new AddFilter()
    setDelta(add, 5)
    add.getFilterIn.linkFrom(graphIn(ngf).getOut)
    val sinkIn = sink(ngf).getInPorts.get(0).asInstanceOf[Node.InPort[Object]]
    sinkIn.linkFrom(add.getFilterOut)

    assertEquals(15.0, src.get(0, null).`val`, 0)
  }

  @Test
  def serialization_roundTrip_keepsGraphInput(): Unit = {
    val src = new FpCont(10)
    val ngf = new NodeGraphFilter()
    src.getFilters.add(ngf)
    val add = new AddFilter()
    setDelta(add, 5)
    add.getFilterIn.linkFrom(graphIn(ngf).getOut)
    sink(ngf).getInPorts.get(0).asInstanceOf[Node.InPort[Object]].linkFrom(add.getFilterOut)

    val copy: FpCont = roundTrip(src)

    val copyNgf = copy.getFilters.get(0).asInstanceOf[NodeGraphFilter]
    assertNotNull(graphIn(copyNgf))
    // 入口节点反向引用宿主 filter，往返后仍能取到图外连入的帧
    assertEquals(15.0, copy.get(0, null).`val`, 0)
  }

  @Test
  def serialization_repairsMissingGraphInput(): Unit = {
    val ngf = new NodeGraphFilter()
    // 模拟改动前的旧存档，入口节点字段与图内节点都不存在
    val field = classOf[NodeGraphFilter].getDeclaredField("innerIn")
    field.setAccessible(true)
    field.set(ngf, null)
    ngf.getInnerNodes.remove(graphIn(ngf))

    val copy: NodeGraphFilter = roundTrip(ngf)

    assertNotNull(graphIn(copy))
    assertEquals(2, copy.getInnerNodes.size())
    assertNotEquals(copy.getInnerNodes.getPosition(graphIn(copy)), copy.getInnerNodes.getPosition(sink(copy)))
    // 补建不自动连线，旧存档里用户已有的连接不被覆盖
    assertFalse(graphIn(copy).getOut.isLinked)
  }

  private def graphIn(ngf: NodeGraphFilter): GraphInNode = {
    ngf.getInnerNodes.asScala.collectFirst { case node: GraphInNode => node }.orNull
  }

  private def sink(ngf: NodeGraphFilter): Sink = {
    ngf.getInnerNodes.asScala.collectFirst { case node: Sink => node }.orNull
  }

  /** AddFilter 的 delta 端口是 filter 私有的，按端口名取值设置默认数据。 */
  private def setDelta(add: AddFilter, value: Double): Unit = {
    add.getInPorts.asScala.find(_.getName == "delta").get.asInstanceOf[Node.InPort[Any]].setDefaultData(value)
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
