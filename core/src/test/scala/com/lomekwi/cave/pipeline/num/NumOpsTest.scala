package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.BinaryNode

/**
 * 验证四个基本运算节点作为 [[BinaryNode]] 实现的行为，包括默认值、各运算符语义、与随机数节点连线。
 */
class NumOpsTest extends munit.FunSuite {

  test("未连接的输入使用中性默认值") {
    assertEqualsDouble(new AddNode().out.getData, 0.0, 0)
    assertEqualsDouble(new SubNode().out.getData, 0.0, 0)
    assertEqualsDouble(new MulNode().out.getData, 1.0, 0)
    assertEqualsDouble(new DivNode().out.getData, 1.0, 0)
  }

  test("各运算求值正确") {
    assertEqualsDouble(eval(new AddNode(), 2, 3), 5.0, 0)
    assertEqualsDouble(eval(new SubNode(), 2, 3), -1.0, 0)
    assertEqualsDouble(eval(new MulNode(), 2, 3), 6.0, 0)
    assertEqualsDouble(eval(new DivNode(), 1, 2), 0.5, 0)
  }

  test("接受随机节点输出") {
    val add = new AddNode()
    add.inA.defaultData = 1.0
    assert(add.inB.linkFrom(new RandomNode().out))

    val v = add.out.getData
    assert(v >= 1.0 && v < 2.0, "越界: " + v)
  }

  test("除零行为遵循 IEEE") {
    assertEqualsDouble(eval(new DivNode(), 1, 0), Double.PositiveInfinity, 0)
  }

  private def eval(node: BinaryNode[Double], a: Double, b: Double): Double = {
    node.inA.defaultData = a
    node.inB.defaultData = b
    node.out.getData
  }
}
