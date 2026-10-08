package com.lomekwi.cave.pipeline

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNull, assertSame}
import org.junit.jupiter.api.Test

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import scala.util.Using

import FilterListTest.*

/**
 * 验证 [[FilterList]]，双向链表行为 + 端口连接自动维护。
 *
 * 覆盖如下。
 * 1) 空列表，[[Segment.get]] 无 filter 时返回源自身帧；
 * 2) add 后端口链 segment.out → f.in → f.out → …；
 * 3) 按索引 add/remove 后连接保持；
 * 4) set 替换后连接更新；
 * 5) clear 后回到空链状态；
 * 6) 按索引 insert/remove 维护连接；
 * 7) 求值，[[Segment.get]] 沿链传播。
 */
class FilterListTest {

  @Test
  def empty_chain_returnsSegmentFrame(): Unit = {
    val segment = new FpCont(10)
    assertEquals(10.0, segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 0)
  }

  @Test
  def add_linksPortsInOrder(): Unit = {
    val segment = new FpCont(10)
    val f1 = new AddFilter()
    val f2 = new AddFilter()
    segment.filters += f1
    segment.filters += f2

    assertSame(segment.source.headOut, f1.filterIn.prev)
    assertSame(f1.filterOut, f2.filterIn.prev)
    assertFalse(f2.filterOut.isLinked)

    f1.delta.defaultData = 1
    f2.delta.defaultData = 2
    assertEquals(13.0, segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 0)
  }

  @Test
  def addAtIndex_keepsChain(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val c = new AddFilter()
    segment.filters += a
    segment.filters += c
    segment.filters.insert(1, b)

    assertSame(segment.source.headOut, a.filterIn.prev)
    assertSame(a.filterOut, b.filterIn.prev)
    assertSame(b.filterOut, c.filterIn.prev)
    assertFalse(c.filterOut.isLinked)

    a.delta.defaultData = 1
    b.delta.defaultData = 2
    c.delta.defaultData = 3
    assertEquals(16.0, segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 0)
  }

  @Test
  def add_atSize_appendsToTail(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    segment.filters += a
    segment.filters.insert(1, b)

    assertEquals(2, segment.filters.size)
    assertEquals(a, segment.filters(0))
    assertEquals(b, segment.filters(1))
    assertSame(a.filterOut, b.filterIn.prev)
  }

  @Test
  def remove_rewiresNeighbors(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val c = new AddFilter()
    segment.filters += a
    segment.filters += b
    segment.filters += c
    segment.filters -= b

    assertEquals(2, segment.filters.size)
    assertSame(segment.source.headOut, a.filterIn.prev)
    assertSame(a.filterOut, c.filterIn.prev)
    assertFalse(c.filterOut.isLinked)
    assertNull(b.filterIn.prev)
    assertFalse(b.filterOut.isLinked)

    a.delta.defaultData = 1
    c.delta.defaultData = 2
    assertEquals(13.0, segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 0)

    // 链表顺序正确
    assertEquals(a, segment.filters(0))
    assertEquals(c, segment.filters(1))
  }

  @Test
  def set_replacesAndUnlinksOld(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    segment.filters += a
    segment.filters += b

    val c = new AddFilter()
    segment.filters.update(0, c)

    assertSame(segment.source.headOut, c.filterIn.prev)
    assertSame(c.filterOut, b.filterIn.prev)
    assertFalse(b.filterOut.isLinked)
    assertNull(a.filterIn.prev)
    assertFalse(a.filterOut.isLinked)

    c.delta.defaultData = 5
    b.delta.defaultData = 1
    assertEquals(16.0, segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 0)
  }

  @Test
  def clear_returnsToEmptyChain(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    segment.filters += a
    segment.filters += b
    segment.filters.clear()

    assertEquals(0, segment.filters.size)
    assertFalse(segment.source.headOut.isLinked)
    assertEquals(10.0, segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 0)
  }

  @Test
  def insert_removeAtIndex_keepsChain(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val filters = segment.filters

    filters += a
    filters += b

    val mid = new AddFilter()
    filters.insert(1, mid)

    assertEquals(3, filters.size)
    assertSame(segment.source.headOut, a.filterIn.prev)
    assertSame(a.filterOut, mid.filterIn.prev)
    assertSame(mid.filterOut, b.filterIn.prev)
    assertFalse(b.filterOut.isLinked)

    assertEquals(mid, filters(1))
    filters.remove(1)
    assertEquals(2, filters.size)
    assertSame(a.filterOut, b.filterIn.prev)
    assertNull(mid.filterIn.prev)
    assertFalse(mid.filterOut.isLinked)
  }

  @Test
  def serialization_roundTrip_restoresChain(): Unit = {
    val segment = new FpCont(10)
    val f1 = new AddFilter()
    f1.delta.defaultData = 3
    segment.filters += f1

    val bos = new ByteArrayOutputStream()
    Using.resource(new ObjectOutputStream(bos)) { oos =>
      oos.writeObject(segment)
    }
    val copy: FpCont = Using.resource(new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) { ois =>
      ois.readObject().asInstanceOf[FpCont]
    }

    assertEquals(1, copy.filters.size)
    assertSame(copy.source.headOut, copy.filters(0).filterIn.prev)
    assertFalse(copy.filters(0).filterOut.isLinked)
    assertEquals(13.0, copy.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 0)
  }
}

object FilterListTest {

  /** 最小可测 [[Filter]]，把传入帧的 val 加 [[delta]]。 */
  private[pipeline] final class AddFilter extends Filter[Fpable] {
    private[FilterListTest] final val delta: Node.InPort[Double] = addInPort(
      new Node.InPort[Double]("delta", 0.0, classOf[Double]) {})
    private final val in: FilterIn = addInPort(new FilterIn("in") {
    })
    private final val out: FilterOut = addOutPort(new FilterOut("out") {
      override def getData: Fpable = {
        val f = filterIn.getData
        if (f != null) {
          f.`val` += delta.getData
        }
        return f
      }
    })

    override def name: String = {
      "加数"
    }
  }

  /** 可复用帧，以 val 为内容。 */
  private[pipeline] final class Fpable(private[pipeline] var `val`: Double) extends Frame(-1)

  private[pipeline] final class FpCont(base: Double) extends Boundless(new FpSource(base))

  /** 以固定 val 产出帧的最小源。 */
  private[pipeline] final class FpSource(private val base: Double) extends Source[Fpable] {
    override protected def produce(time: Long, track: com.lomekwi.cave.timeline.Track, segment: Segment): Fpable = {
      return new Fpable(base)
    }

    override def getLengthPerExportFrame: Long = {
      1
    }

    override def getDuration: Long = {
      Long.MaxValue
    }

    override def getDefaultDuration: Option[Long] = {
      None
    }

    override def displayName: String = {
      "数字源"
    }

    override def createTlSegmentActor(segment: Segment): com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor = {
      null
    }
  }
}
