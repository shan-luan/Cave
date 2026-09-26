package com.lomekwi.cave.pipeline

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNull, assertSame}
import org.junit.jupiter.api.Test

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.util.List

import scala.util.Using

import FilterListTest.*

/**
 * 验证 [[FilterList]]，双向链表行为 + 端口连接自动维护。
 *
 * 覆盖如下。
 * 1) 空列表，Segment.get() 无 filter 时返回源自身帧；
 * 2) add 后端口链 segment.out → f.in → f.out → …；
 * 3) 按索引 add/remove 后连接保持；
 * 4) set 替换后连接更新；
 * 5) clear 后回到空链状态；
 * 6) listIterator 的 add/remove/set 维护连接；
 * 7) 求值，Segment.get() 沿链传播。
 */
class FilterListTest {

  @Test
  def empty_chain_returnsSegmentFrame(): Unit = {
    val segment = new FpCont(10)
    assertEquals(10.0, segment.get(0, null).`val`, 0)
  }

  @Test
  def add_linksPortsInOrder(): Unit = {
    val segment = new FpCont(10)
    val f1 = new AddFilter()
    val f2 = new AddFilter()
    segment.getFilters.add(f1)
    segment.getFilters.add(f2)

    assertSame(segment.getSource.headOut, f1.getFilterIn.getPrev)
    assertSame(f1.getFilterOut, f2.getFilterIn.getPrev)
    assertFalse(f2.getFilterOut.isLinked)

    f1.delta.setDefaultData(1)
    f2.delta.setDefaultData(2)
    assertEquals(13.0, segment.get(0, null).`val`, 0)
  }

  @Test
  def addAtIndex_keepsChain(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val c = new AddFilter()
    segment.getFilters.add(a)
    segment.getFilters.add(c)
    segment.getFilters.add(1, b)

    assertSame(segment.getSource.headOut, a.getFilterIn.getPrev)
    assertSame(a.getFilterOut, b.getFilterIn.getPrev)
    assertSame(b.getFilterOut, c.getFilterIn.getPrev)
    assertFalse(c.getFilterOut.isLinked)

    a.delta.setDefaultData(1)
    b.delta.setDefaultData(2)
    c.delta.setDefaultData(3)
    assertEquals(16.0, segment.get(0, null).`val`, 0)
  }

  @Test
  def add_atSize_appendsToTail(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    segment.getFilters.add(a)
    segment.getFilters.add(1, b)

    assertEquals(2, segment.getFilters.size())
    assertEquals(a, segment.getFilters.get(0))
    assertEquals(b, segment.getFilters.get(1))
    assertSame(a.getFilterOut, b.getFilterIn.getPrev)
  }

  @Test
  def remove_rewiresNeighbors(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val c = new AddFilter()
    segment.getFilters.add(a)
    segment.getFilters.add(b)
    segment.getFilters.add(c)
    segment.getFilters.remove(b)

    assertEquals(2, segment.getFilters.size())
    assertSame(segment.getSource.headOut, a.getFilterIn.getPrev)
    assertSame(a.getFilterOut, c.getFilterIn.getPrev)
    assertFalse(c.getFilterOut.isLinked)
    assertNull(b.getFilterIn.getPrev)
    assertFalse(b.getFilterOut.isLinked)

    a.delta.setDefaultData(1)
    c.delta.setDefaultData(2)
    assertEquals(13.0, segment.get(0, null).`val`, 0)

    // 链表顺序正确
    assertEquals(a, segment.getFilters.get(0))
    assertEquals(c, segment.getFilters.get(1))
  }

  @Test
  def set_replacesAndUnlinksOld(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    segment.getFilters.add(a)
    segment.getFilters.add(b)

    val c = new AddFilter()
    segment.getFilters.set(0, c)

    assertSame(segment.getSource.headOut, c.getFilterIn.getPrev)
    assertSame(c.getFilterOut, b.getFilterIn.getPrev)
    assertFalse(b.getFilterOut.isLinked)
    assertNull(a.getFilterIn.getPrev)
    assertFalse(a.getFilterOut.isLinked)

    c.delta.setDefaultData(5)
    b.delta.setDefaultData(1)
    assertEquals(16.0, segment.get(0, null).`val`, 0)
  }

  @Test
  def clear_returnsToEmptyChain(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    segment.getFilters.add(a)
    segment.getFilters.add(b)
    segment.getFilters.clear()

    assertEquals(0, segment.getFilters.size())
    assertFalse(segment.getSource.headOut.isLinked)
    assertEquals(10.0, segment.get(0, null).`val`, 0)
  }

  @Test
  def listIterator_add_remove_keepsChain(): Unit = {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val filters: List[Filter[Fpable]] = segment.getFilters.asInstanceOf[List[Filter[Fpable]]]

    filters.add(a)
    filters.add(b)

    val mid = new AddFilter()
    var lit = filters.listIterator(1)
    lit.add(mid)

    assertEquals(3, filters.size())
    assertSame(segment.getSource.headOut, a.getFilterIn.getPrev)
    assertSame(a.getFilterOut, mid.getFilterIn.getPrev)
    assertSame(mid.getFilterOut, b.getFilterIn.getPrev)
    assertFalse(b.getFilterOut.isLinked)

    lit = filters.listIterator(1)
    assertEquals(mid, lit.next())
    lit.remove()
    assertEquals(2, filters.size())
    assertSame(a.getFilterOut, b.getFilterIn.getPrev)
    assertNull(mid.getFilterIn.getPrev)
    assertFalse(mid.getFilterOut.isLinked)
  }

  @Test
  def serialization_roundTrip_restoresChain(): Unit = {
    val segment = new FpCont(10)
    val f1 = new AddFilter()
    f1.delta.setDefaultData(3)
    segment.getFilters.add(f1)

    val bos = new ByteArrayOutputStream()
    Using.resource(new ObjectOutputStream(bos)) { oos =>
      oos.writeObject(segment)
    }
    val copy: FpCont = Using.resource(new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) { ois =>
      ois.readObject().asInstanceOf[FpCont]
    }

    assertEquals(1, copy.getFilters.size())
    assertSame(copy.getSource.headOut, copy.getFilters.get(0).getFilterIn.getPrev)
    assertFalse(copy.getFilters.get(0).getFilterOut.isLinked)
    assertEquals(13.0, copy.get(0, null).`val`, 0)
  }
}

object FilterListTest {

  /** 最小可测 Filter，把传入帧的 val 加 delta。 */
  private[pipeline] final class AddFilter extends Filter[Fpable] {
    private[FilterListTest] final val delta: Node.InPort[Double] = addInPort(
      new Node.InPort[Double]("delta", 0.0, classOf[Double]) {})
    private final val in: FilterIn = addInPort(new FilterIn("in") {
    })
    private final val out: FilterOut = addOutPort(new FilterOut("out") {
      override def getData: Fpable = {
        val f = getFilterIn.getData
        if (f != null) {
          f.`val` += delta.getData
        }
        return f
      }
    })

    override def getName: String = {
      "加数"
    }
  }

  /** 可复用帧，以 val 为内容。 */
  private[pipeline] final class Fpable(private[pipeline] var `val`: Double) extends Frame(-1)

  private[pipeline] final class FpCont(base: Double) extends Content[Fpable](new FpSource(base))

  /** 以固定 val 产出帧的最小源。 */
  private[pipeline] final class FpSource(private val base: Double) extends Source[Fpable] {
    override protected def produce(time: Long, track: com.lomekwi.cave.timeline.Track, segment: Segment[Fpable]): Fpable = {
      return new Fpable(base)
    }

    override def getLengthPerExportFrame: Long = {
      1
    }

    override def getDuration: Long = {
      Long.MaxValue
    }

    override def getDisplayName: String = {
      "数字源"
    }

    override def createTlSegmentActor(segment: Segment[?]): com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor = {
      null
    }
  }
}
