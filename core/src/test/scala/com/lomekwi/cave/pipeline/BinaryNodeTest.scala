package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.FilterListTest.{Fpable, FpCont}
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull, assertNotSame, assertNull, assertSame, assertThrows}
import org.junit.jupiter.api.Test

import java.util

/**
 * 验证 BinaryNode 抽象节点，两个主输入 In 与一个主输出 Out 的数量约束、类型推断与求值。
 */
class BinaryNodeTest {

  @Test
  def hasTwoInputsAndOneOutput(): Unit = {
    val node = new SumNode()
    assertEquals(2, node.getInPorts.size())
    assertEquals(1, node.getOutPorts.size())
    assertNotSame(node.getInA, node.getInB)
    assertSame(node.getInA, node.getInPorts.get(0))
    assertSame(node.getInB, node.getInPorts.get(1))
    assertSame(node.getOut, node.getOutPorts.get(0))
  }

  @Test
  def thirdIn_throws(): Unit = {
    assertThrows(classOf[IllegalStateException], () => new ThreeInNode())
  }

  @Test
  def secondOut_throws(): Unit = {
    assertThrows(classOf[IllegalStateException], () => new TwoOutNode())
  }

  @Test
  def outputType_unknownWhenNoInputLinked(): Unit = {
    assertNull(new SumNode().getOut.getType)
  }

  @Test
  def outputType_followsInputA(): Unit = {
    val segment = new FpCont(1)
    val node = new SumNode()
    node.getInA.linkFrom(segment.getSource.headOut)
    assertSame(classOf[Fpable], node.getOut.getType)
  }

  @Test
  def outputType_fallsBackToInputB(): Unit = {
    val segment = new FpCont(1)
    val node = new SumNode()
    node.getInB.linkFrom(segment.getSource.headOut)
    assertSame(classOf[Fpable], node.getOut.getType)
  }

  @Test
  def constraint_mergesDownstream(): Unit = {
    val node = new SumNode()
    assertEquals(util.Set.of(classOf[Fpable]), node.getInA.getConstraint)

    val downstream = new Node.InPort[AnyRef]("下游", classOf[AnyRef])
    node.getOut.link(downstream)
    assertEquals(util.Set.of(classOf[Fpable], classOf[AnyRef]), node.getInA.getConstraint)
    assertEquals(util.Set.of(classOf[Fpable], classOf[AnyRef]), node.getInB.getConstraint)
  }

  @Test
  def evaluatesBothInputs(): Unit = {
    val a = new FpCont(2)
    val b = new FpCont(3)
    val node = new SumNode()
    node.getInA.linkFrom(a.getSource.headOut)
    node.getInB.linkFrom(b.getSource.headOut)
    a.get(0, null)
    b.get(0, null)

    assertEquals(5.0, node.getOut.getData.`val`, 0)
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

  override def getName: String = "求和"
}

private final class ThreeInNode extends BinaryNode[Fpable] {
  addInPort(new In("A"))
  addInPort(new In("B"))
  addInPort(new In("C"))

  override def getName: String = "三入"
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

  override def getName: String = "双出"
}
