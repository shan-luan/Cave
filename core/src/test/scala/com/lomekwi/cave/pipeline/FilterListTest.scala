package com.lomekwi.cave.pipeline

import org.junit.Assert.{assertEquals, assertFalse, assertNull, assertSame, assertTrue}
import org.junit.Test

import com.lomekwi.cave.pipeline.num.NumFrame

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.util.{Iterator, List, Set}

import scala.util.Using

import FilterListTest.*

/**
 * 验证 {@link FilterList}：双向链表行为 + 端口连接自动维护。
 *
 * 覆盖：
 * 1) 空列表：Source.get() 无 filter 时返回源自身帧；
 * 2) add 后端口链 source.out → f.in → f.out → …；
 * 3) 按索引 add/remove 后连接保持；
 * 4) set 替换后连接更新；
 * 5) clear 后回到空链状态；
 * 6) listIterator 的 add/remove/set 维护连接；
 * 7) 求值：Source.get() 沿链传播。
 */
class FilterListTest {

  @Test
  def empty_chain_returnsSourceFrame(): Unit = {
    val src = new NumSrc(10)
    assertEquals(10.0, src.get(0, null).`val`, 0)
  }

  @Test
  def add_linksPortsInOrder(): Unit = {
    val src = new NumSrc(10)
    val f1 = new AddFilter()
    val f2 = new AddFilter()
    src.getFilters().add(f1)
    src.getFilters().add(f2)

    // source.out → f1.in
    assertSame(src.headOut, f1.getFilterIn().getPrev())
    // f1.out → f2.in
    assertSame(f1.getFilterOut(), f2.getFilterIn().getPrev())
    // 链末端：最后一个 filter 的 out 不连接
    assertFalse(f2.getFilterOut().isLinked())

    f1.delta.getDefaultData().setVal(1)
    f2.delta.getDefaultData().setVal(2)
    assertEquals(13.0, src.get(0, null).`val`, 0)
  }

  @Test
  def addAtIndex_keepsChain(): Unit = {
    val src = new NumSrc(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val c = new AddFilter()
    src.getFilters().add(a)
    src.getFilters().add(c)
    src.getFilters().add(1, b)

    assertSame(src.headOut, a.getFilterIn().getPrev())
    assertSame(a.getFilterOut(), b.getFilterIn().getPrev())
    assertSame(b.getFilterOut(), c.getFilterIn().getPrev())
    assertFalse(c.getFilterOut().isLinked())

    a.delta.getDefaultData().setVal(1)
    b.delta.getDefaultData().setVal(2)
    c.delta.getDefaultData().setVal(3)
    assertEquals(16.0, src.get(0, null).`val`, 0)
  }

  @Test
  def add_atSize_appendsToTail(): Unit = {
    val src = new NumSrc(10)
    val a = new AddFilter()
    val b = new AddFilter()
    src.getFilters().add(a)
    // add(size) 是合法的追加语义，曾因 entryAt 越界而抛 IndexOutOfBounds
    src.getFilters().add(1, b)

    assertEquals(2, src.getFilters().size())
    assertEquals(a, src.getFilters().get(0))
    assertEquals(b, src.getFilters().get(1))
    assertSame(a.getFilterOut(), b.getFilterIn().getPrev())
  }

  @Test
  def remove_rewiresNeighbors(): Unit = {
    val src = new NumSrc(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val c = new AddFilter()
    src.getFilters().add(a)
    src.getFilters().add(b)
    src.getFilters().add(c)
    src.getFilters().remove(b)

    assertEquals(2, src.getFilters().size())
    assertSame(src.headOut, a.getFilterIn().getPrev())
    assertSame(a.getFilterOut(), c.getFilterIn().getPrev())
    assertFalse(c.getFilterOut().isLinked())
    // b 被移除且解除连接
    assertNull(b.getFilterIn().getPrev())
    assertFalse(b.getFilterOut().isLinked())

    a.delta.getDefaultData().setVal(1)
    c.delta.getDefaultData().setVal(2)
    assertEquals(13.0, src.get(0, null).`val`, 0)

    // 链表顺序正确（regression：unlink 曾漏更新 pred.next/next.prev，导致残骸节点残留）
    assertEquals(a, src.getFilters().get(0))
    assertEquals(c, src.getFilters().get(1))
  }

  @Test
  def set_replacesAndUnlinksOld(): Unit = {
    val src = new NumSrc(10)
    val a = new AddFilter()
    val b = new AddFilter()
    src.getFilters().add(a)
    src.getFilters().add(b)

    val c = new AddFilter()
    src.getFilters().set(0, c)

    assertSame(src.headOut, c.getFilterIn().getPrev())
    assertSame(c.getFilterOut(), b.getFilterIn().getPrev())
    assertFalse(b.getFilterOut().isLinked())
    assertNull(a.getFilterIn().getPrev())
    assertFalse(a.getFilterOut().isLinked())

    c.delta.getDefaultData().setVal(5)
    b.delta.getDefaultData().setVal(1)
    assertEquals(16.0, src.get(0, null).`val`, 0)
  }

  @Test
  def clear_returnsToEmptyChain(): Unit = {
    val src = new NumSrc(10)
    val a = new AddFilter()
    val b = new AddFilter()
    src.getFilters().add(a)
    src.getFilters().add(b)
    src.getFilters().clear()

    assertEquals(0, src.getFilters().size())
    assertFalse(src.headOut.isLinked())
    assertEquals(10.0, src.get(0, null).`val`, 0)
  }

  @Test
  def listIterator_add_remove_keepsChain(): Unit = {
    val src = new NumSrc(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val filters: List[Filter[Numable]] = src.getFilters().asInstanceOf[List[Filter[Numable]]]

    // 先 add 两个
    filters.add(a)
    filters.add(b)

    // iterator add 到中间
    val mid = new AddFilter()
    var lit = filters.listIterator(1)
    lit.add(mid)

    assertEquals(3, filters.size())
    assertSame(src.headOut, a.getFilterIn().getPrev())
    assertSame(a.getFilterOut(), mid.getFilterIn().getPrev())
    assertSame(mid.getFilterOut(), b.getFilterIn().getPrev())
    assertFalse(b.getFilterOut().isLinked())

    // iterator remove 中间
    lit = filters.listIterator(1)
    assertEquals(mid, lit.next()) // 返回 mid
    lit.remove()
    assertEquals(2, filters.size())
    assertSame(a.getFilterOut(), b.getFilterIn().getPrev())
    assertNull(mid.getFilterIn().getPrev())
    assertFalse(mid.getFilterOut().isLinked())
  }

  @Test
  def serialization_roundTrip_restoresChain(): Unit = {
    val src = new NumSrc(10)
    val f1 = new AddFilter()
    f1.delta.getDefaultData().setVal(3)
    src.getFilters().add(f1)

    val bos = new ByteArrayOutputStream()
    Using.resource(new ObjectOutputStream(bos)) { oos =>
      oos.writeObject(src)
    }
    val copy: NumSrc = Using.resource(new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) { ois =>
      ois.readObject().asInstanceOf[NumSrc]
    }

    assertEquals(1, copy.getFilters().size())
    // 端口连接恢复
    assertSame(copy.headOut, copy.getFilters().get(0).getFilterIn().getPrev())
    assertFalse(copy.getFilters().get(0).getFilterOut().isLinked())
    // 求值正确
    assertEquals(13.0, copy.get(0, null).`val`, 0)
  }
}

object FilterListTest {

  /** 最小可测 Filter：把传入帧的 val 加 delta。 */
  private[pipeline] final class AddFilter extends Filter[Numable] {
    private[FilterListTest] final val delta: Node.InPort[NumFrame] = addInPort(
      new Node.InPort[NumFrame]("delta", new NumFrame(null), classOf[NumFrame]) {})
    private final val in: FilterIn = addInPort(new FilterIn("in") {
    })
    private final val out: FilterOut = addOutPort(new FilterOut("out") {
      override def getData(): Numable = {
        val f = getFilterIn().getData()
        if (f != null) {
          f.`val` += delta.getData().getVal()
        }
        return f
      }
    })

    override def getType(): Class[Numable] = {
      classOf[Numable]
    }

    override def getName(): String = {
      "加数"
    }
  }

  /** 可复用帧：以 val 为内容。 */
  private[pipeline] final class Numable(private[pipeline] var `val`: Double) extends Frame(null)

  private[pipeline] final class NumSrc(private var base: Double) extends Source[Numable] {
    override protected def generate(time: Long, track: com.lomekwi.cave.timeline.Track): Numable = {
      return new Numable(base)
    }

    override def sync(time: Long, track: com.lomekwi.cave.timeline.Track): Unit = {
    }

    override def getLengthPerExportFrame(): Long = {
      1
    }

    override def getDuration(): Long = {
      Long.MaxValue
    }

    override def getDisplayName(): String = {
      "数字源"
    }

    override def getFrameType(): Class[Numable] = {
      classOf[Numable]
    }

    override def createSegActor(segment: com.lomekwi.cave.timeline.Segment): com.lomekwi.cave.ui.editpanel.tlarea.SegActor = {
      null
    }
  }
}
