package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.FilterListTest.{Fpable, FpCont}

/**
 * 验证 [[BinaryNode]] 抽象节点，两个主输入 [[BinaryNode.In]] 与一个主输出 [[BinaryNode.Out]] 的数量约束、类型推断与求值。
 */
class BinaryNodeTest extends munit.FunSuite {

  test("具有两个输入与一个输出") {
    val node = new SumNode()
    assertEquals(node.inPorts.size, 2)
    assertEquals(node.outPorts.size, 1)
    assert(!((node.inA) eq (node.inB)))
    assert((node.inA) eq (node.inPorts(0)))
    assert((node.inB) eq (node.inPorts(1)))
    assert((node.out) eq (node.outPorts(0)))
  }

  test("添加第三个输入时抛出异常") {
    intercept[IllegalStateException] { new ThreeInNode() }
  }

  test("添加第二个输出时抛出异常") {
    intercept[IllegalStateException] { new TwoOutNode() }
  }

  test("未连接输入时输出类型未知") {
    assert((new SumNode().out.getType) == null)
  }

  test("输出类型跟随输入 A") {
    val segment = new FpCont(1)
    val node = new SumNode()
    node.inA.linkFrom(segment.source.headOut)
    assert((classOf[Fpable]) eq (node.out.getType))
  }

  test("输出类型回退到输入 B") {
    val segment = new FpCont(1)
    val node = new SumNode()
    node.inB.linkFrom(segment.source.headOut)
    assert((classOf[Fpable]) eq (node.out.getType))
  }

  test("约束向下游合并") {
    val node = new SumNode()
    assertEquals(node.inA.constraint, Set[Class[?]](classOf[Fpable]))

    val downstream = new Node.InPort[AnyRef]("下游", classOf[AnyRef])
    node.out.link(downstream)
    assertEquals(node.inA.constraint, Set[Class[?]](classOf[Fpable], classOf[AnyRef]))
    assertEquals(node.inB.constraint, Set[Class[?]](classOf[Fpable], classOf[AnyRef]))
  }

  test("求值时同时使用两个输入") {
    val a = new FpCont(2)
    val b = new FpCont(3)
    val node = new SumNode()
    node.inA.linkFrom(a.source.headOut)
    node.inB.linkFrom(b.source.headOut)
    a.get(0, null)
    b.get(0, null)

    assertEqualsDouble(node.out.getData.`val`, 5.0, 0)
  }
}

/** 最小可测二元节点，把两个输入的 val 相加。 */
private final class SumNode extends BinaryNode[Fpable] {
  private final val inA: In = addInPort(new In("A"))
  private final val inB: In = addInPort(new In("B"))
  private final val out: Out = addOutPort(new Out("输出") {
    override def getData: Fpable = {
      val a = inA.getData
      val b = inB.getData
      if (a != null && b != null) {
        a.`val` += b.`val`
      }
      a
    }
  })

  override def name: String = "求和"
}

private final class ThreeInNode extends BinaryNode[Fpable] {
  addInPort(new In("A"))
  addInPort(new In("B"))
  addInPort(new In("C"))

  override def name: String = "三入"
}

private final class TwoOutNode extends BinaryNode[Fpable] {
  addInPort(new In("A"))
  addInPort(new In("B"))
  addOutPort(new Out("输出") {
    override def getData: Fpable = null
  })
  addOutPort(new Out("输出2") {
    override def getData: Fpable = null
  })

  override def name: String = "双出"
}
