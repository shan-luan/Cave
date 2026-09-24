package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.NodeRegistry
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

/**
 * 验证随机数节点的取值语义，输出 [0,1) 的 Double，且每次求值都重新随机。
 */
class RandomNodeTest {

  @Test
  def output_isInUnitInterval(): Unit = {
    val out = new RandomNode().getOut
    assertEquals(classOf[Double], out.getType)

    var i = 0
    while (i < 1000) {
      val v = out.getData
      assertTrue(v >= 0.0 && v < 1.0, "越界: " + v)
      i += 1
    }
  }

  @Test
  def output_reRandomizesEachEvaluation(): Unit = {
    val out = new RandomNode().getOut
    val first = out.getData

    var changed = false
    var i = 0
    while (i < 100 && !changed) {
      if (out.getData != first) changed = true
      i += 1
    }
    assertTrue(changed, "每次求值应返回新的随机数")
  }

  @Test
  def registered_inNodeRegistry(): Unit = {
    val registry = new NodeRegistry()
    val registered = (0 until registry.getCount).exists(i => registry.create(i).isInstanceOf[RandomNode])
    assertTrue(registered, "随机数节点应已注册")
  }
}
