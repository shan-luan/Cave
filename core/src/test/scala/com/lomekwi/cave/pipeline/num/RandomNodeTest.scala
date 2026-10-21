package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.NodeRegistry

/**
 * 验证随机数节点的取值语义，输出 [0,1) 的 Double，且每次求值都重新随机。
 */
class RandomNodeTest extends munit.FunSuite {

  test("输出位于单位区间") {
    val out = new RandomNode().out
    assertEquals(out.getType, classOf[Double])

    var i = 0
    while (i < 1000) {
      val v = out.getData
      assert(v >= 0.0 && v < 1.0, "越界: " + v)
      i += 1
    }
  }

  test("每次求值重新随机") {
    val out = new RandomNode().out
    val first = out.getData

    var changed = false
    var i = 0
    while (i < 100 && !changed) {
      if (out.getData != first) changed = true
      i += 1
    }
    assert(changed, "每次求值应返回新的随机数")
  }

  test("已注册到节点注册表") {
    val registry = new NodeRegistry()
    val registered = (0 until registry.getCount).exists(i => registry.create(i).isInstanceOf[RandomNode])
    assert(registered, "随机数节点应已注册")
  }
}
