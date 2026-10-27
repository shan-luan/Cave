package com.lomekwi.cave.pipeline

import com.lomekwi.cave.project.TestIds
import com.lomekwi.cave.timeline.{Boundless, Segment}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import scala.util.Using

import FilterListTest.*

/**
 * 验证 [[FilterList]]，双向链表行为 + 端口连接自动维护。
 */
class FilterListTest extends munit.FunSuite {

  test("空滤镜链直接返回片段帧") {
    val segment = new FpCont(10)
    assertEqualsDouble(segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 10.0, 0)
  }

  test("添加滤镜按顺序连接端口") {
    val segment = new FpCont(10)
    val f1 = new AddFilter()
    val f2 = new AddFilter()
    segment.filters += f1
    segment.filters += f2

    assert((segment.source.headOut) eq (f1.filterIn.prev))
    assert((f1.filterOut) eq (f2.filterIn.prev))
    assert(!(f2.filterOut.isLinked))

    f1.delta.defaultData = 1
    f2.delta.defaultData = 2
    assertEqualsDouble(segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 13.0, 0)
  }

  test("指定下标插入后链保持完整") {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val c = new AddFilter()
    segment.filters += a
    segment.filters += c
    segment.filters.insert(1, b)

    assert((segment.source.headOut) eq (a.filterIn.prev))
    assert((a.filterOut) eq (b.filterIn.prev))
    assert((b.filterOut) eq (c.filterIn.prev))
    assert(!(c.filterOut.isLinked))

    a.delta.defaultData = 1
    b.delta.defaultData = 2
    c.delta.defaultData = 3
    assertEqualsDouble(segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 16.0, 0)
  }

  test("在下标等于长度时追加到链尾") {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    segment.filters += a
    segment.filters.insert(1, b)

    assertEquals(segment.filters.size, 2)
    assertEquals(segment.filters(0), a)
    assertEquals(segment.filters(1), b)
    assert((a.filterOut) eq (b.filterIn.prev))
  }

  test("删除滤镜后重连邻居") {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val c = new AddFilter()
    segment.filters += a
    segment.filters += b
    segment.filters += c
    segment.filters -= b

    assertEquals(segment.filters.size, 2)
    assert((segment.source.headOut) eq (a.filterIn.prev))
    assert((a.filterOut) eq (c.filterIn.prev))
    assert(!(c.filterOut.isLinked))
    assert((b.filterIn.prev) == null)
    assert(!(b.filterOut.isLinked))

    a.delta.defaultData = 1
    c.delta.defaultData = 2
    assertEqualsDouble(segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 13.0, 0)

    assertEquals(segment.filters(0), a)
    assertEquals(segment.filters(1), c)
  }

  test("set 替换并断开旧滤镜") {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    segment.filters += a
    segment.filters += b

    val c = new AddFilter()
    segment.filters.update(0, c)

    assert((segment.source.headOut) eq (c.filterIn.prev))
    assert((c.filterOut) eq (b.filterIn.prev))
    assert(!(b.filterOut.isLinked))
    assert((a.filterIn.prev) == null)
    assert(!(a.filterOut.isLinked))

    c.delta.defaultData = 5
    b.delta.defaultData = 1
    assertEqualsDouble(segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 16.0, 0)
  }

  test("clear 恢复为空链") {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    segment.filters += a
    segment.filters += b
    segment.filters.clear()

    assertEquals(segment.filters.size, 0)
    assert(!(segment.source.headOut.isLinked))
    assertEqualsDouble(segment.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 10.0, 0)
  }

  test("按下标插入与删除后链保持完整") {
    val segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val filters = segment.filters

    filters += a
    filters += b

    val mid = new AddFilter()
    filters.insert(1, mid)

    assertEquals(filters.size, 3)
    assert((segment.source.headOut) eq (a.filterIn.prev))
    assert((a.filterOut) eq (mid.filterIn.prev))
    assert((mid.filterOut) eq (b.filterIn.prev))
    assert(!(b.filterOut.isLinked))

    assertEquals(filters(1), mid)
    filters.remove(1)
    assertEquals(filters.size, 2)
    assert((a.filterOut) eq (b.filterIn.prev))
    assert((mid.filterIn.prev) == null)
    assert(!(mid.filterOut.isLinked))
  }

  test("序列化往返恢复滤镜链") {
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

    assertEquals(copy.filters.size, 1)
    assert((copy.source.headOut) eq (copy.filters(0).filterIn.prev))
    assert(!(copy.filters(0).filterOut.isLinked))
    assertEqualsDouble(copy.get(0, null).asInstanceOf[FilterListTest.Fpable].`val`, 13.0, 0)
  }
}

object FilterListTest {

  /** 最小可测 [[Filter]]，把传入帧的 val 加 [[delta]]。 */
  private[pipeline] final class AddFilter extends Filter[Fpable] {
    private[FilterListTest] val delta: Node.InPort[Double] = addInPort(
      new Node.InPort[Double]("delta", 0.0, classOf[Double]) {})
    private val in: FilterIn = addInPort(new FilterIn("in") {
    })
    private val out: FilterOut = addOutPort(new FilterOut("out") {
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

  private[pipeline] final class FpCont(base: Double) extends Boundless(TestIds.nextId(), new FpSource(base))

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
