package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.BinaryNode
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

/**
 * 验证四个基本运算节点作为 BinaryNode 实现的行为，包括默认值、各运算符语义、与随机数节点连线。
 */
class NumOpsTest {

  @Test
  def unconnectedInputs_useNeutralDefaults(): Unit = {
    assertEquals(0.0, new AddNode().getOut.getData, 0)
    assertEquals(0.0, new SubNode().getOut.getData, 0)
    assertEquals(1.0, new MulNode().getOut.getData, 0)
    assertEquals(1.0, new DivNode().getOut.getData, 0)
  }

  @Test
  def computesEachOperation(): Unit = {
    assertEquals(5.0, eval(new AddNode(), 2, 3), 0)
    assertEquals(-1.0, eval(new SubNode(), 2, 3), 0)
    assertEquals(6.0, eval(new MulNode(), 2, 3), 0)
    assertEquals(0.5, eval(new DivNode(), 1, 2), 0)
  }

  @Test
  def acceptsRandomNodeOutput(): Unit = {
    val add = new AddNode()
    add.getInA.setDefaultData(1.0)
    assertTrue(add.getInB.linkFrom(new RandomNode().getOut))

    val v = add.getOut.getData
    assertTrue(v >= 1.0 && v < 2.0, "越界: " + v)
  }

  @Test
  def divideByZero_followsIeee(): Unit = {
    assertEquals(Double.PositiveInfinity, eval(new DivNode(), 1, 0), 0)
  }

  private def eval(node: BinaryNode[Double], a: Double, b: Double): Double = {
    node.getInA.setDefaultData(a)
    node.getInB.setDefaultData(b)
    node.getOut.getData
  }
}
