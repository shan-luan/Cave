package com.lomekwi.cave.pipeline

import com.lomekwi.cave.project.{IdSource, TestIds}
import com.lomekwi.cave.timeline.{Boundless, Segment, Timespan}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import scala.util.Using

import FilterChainTest.*

/**
 * 验证片段滤镜链的顺序与求值。
 */
class FilterChainTest extends munit.FunSuite {
  given IdSource = TestIds

  test("空滤镜链直接返回片段帧") {
    val segment = new FpCont(10)
    assertEqualsDouble(segment.get(Timespan.Zero, null).asInstanceOf[Fpable].`val`, 10.0, 0)
  }

  test("添加滤镜按顺序求值") {
    var segment: Segment = new FpCont(10)
    val f1 = new AddFilter()
    val f2 = new AddFilter()
    segment = segment.addFilter(f1)
    segment = segment.addFilter(f2)

    segment = segment.setFilterDefault(f1.delta, 1.0)
    segment = segment.setFilterDefault(f2.delta, 2.0)
    assertEqualsDouble(segment.get(Timespan.Zero, null).asInstanceOf[Fpable].`val`, 13.0, 0)
    assertEquals(segment.filters, Vector(f1, f2))
  }

  test("指定下标插入后按顺序求值") {
    var segment: Segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val c = new AddFilter()
    segment = segment.addFilter(a)
    segment = segment.addFilter(c)
    segment = segment.insertFilter(1, b)

    segment = segment.setFilterDefault(a.delta, 1.0)
    segment = segment.setFilterDefault(b.delta, 2.0)
    segment = segment.setFilterDefault(c.delta, 3.0)
    assertEqualsDouble(segment.get(Timespan.Zero, null).asInstanceOf[Fpable].`val`, 16.0, 0)
    assertEquals(segment.filters, Vector(a, b, c))
  }

  test("删除滤镜后保持剩余顺序") {
    var segment: Segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val c = new AddFilter()
    segment = segment.addFilter(a)
    segment = segment.addFilter(b)
    segment = segment.addFilter(c)
    segment = segment.removeFilter(b)

    segment = segment.setFilterDefault(a.delta, 1.0)
    segment = segment.setFilterDefault(c.delta, 2.0)
    assertEqualsDouble(segment.get(Timespan.Zero, null).asInstanceOf[Fpable].`val`, 13.0, 0)
    assertEquals(segment.filters, Vector(a, c))
  }

  test("替换滤镜后按新顺序求值") {
    var segment: Segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    segment = segment.addFilter(a)
    segment = segment.addFilter(b)

    val c = new AddFilter()
    segment = segment.replaceFilter(0, c)

    segment = segment.setFilterDefault(c.delta, 5.0)
    segment = segment.setFilterDefault(b.delta, 1.0)
    assertEqualsDouble(segment.get(Timespan.Zero, null).asInstanceOf[Fpable].`val`, 16.0, 0)
    assertEquals(segment.filters, Vector(c, b))
  }

  test("重排滤镜后保持新顺序") {
    var segment: Segment = new FpCont(10)
    val a = new AddFilter()
    val b = new AddFilter()
    val c = new AddFilter()
    segment = segment.addFilter(a)
    segment = segment.addFilter(b)
    segment = segment.addFilter(c)

    segment = segment.moveFilter(a, 2)
    assertEquals(segment.filters, Vector(b, c, a))
    segment = segment.moveFilter(a, 0)
    assertEquals(segment.filters, Vector(a, b, c))
  }

  test("clear 恢复为空链") {
    var segment: Segment = new FpCont(10)
    segment = segment.addFilter(new AddFilter())
    segment = segment.addFilter(new AddFilter())
    segment = segment.clearFilters()

    assertEquals(segment.filters.size, 0)
    assertEqualsDouble(segment.get(Timespan.Zero, null).asInstanceOf[Fpable].`val`, 10.0, 0)
  }

  test("序列化往返恢复滤镜链") {
    var segment: Segment = new FpCont(10)
    val f1 = new AddFilter()
    segment = segment.addFilter(f1)
    segment = segment.setFilterDefault(f1.delta, 3.0)
    assertEquals(segment.filters.size, 1)

    val bos = new ByteArrayOutputStream()
    Using.resource(new ObjectOutputStream(bos)) { oos =>
      oos.writeObject(segment)
    }
    val copy: Segment = Using.resource(new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) { ois =>
      ois.readObject().asInstanceOf[Segment]
    }

    assertEquals(copy.filters.size, 1)
    assertEqualsDouble(copy.get(Timespan.Zero, null).asInstanceOf[Fpable].`val`, 13.0, 0)
  }
}

object FilterChainTest {

  /** 最小可测 [[Filter]]，把传入帧的 val 加 [[delta]]。 */
  private[pipeline] final class AddFilter(delta0: Double = 0.0) extends Filter[Fpable] {
    private[FilterChainTest] val delta: Node.InPort[Double] = addInPort(
      new Node.InPort[Double]("delta", delta0, classOf[Double]) {})
    addInPort(new FilterIn("in") {
    })
    addOutPort(new FilterOut("out") {
      override def getData(context: FilterChain | NodeGraph): Fpable = {
        val f = filterIn.getData(context)
        if (f != null) {
          f.`val` += delta.getData(context)
        }
        f
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
    override protected def produce(time: Timespan, track: com.lomekwi.cave.timeline.Track, segment: Segment): Fpable = {
      new Fpable(base)
    }

    override def getLengthPerExportFrame: Timespan = {
      Timespan(1)
    }

    override def getDuration: Timespan = {
      Timespan.MaxValue
    }

    override def getDefaultDuration: Option[Timespan] = {
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
