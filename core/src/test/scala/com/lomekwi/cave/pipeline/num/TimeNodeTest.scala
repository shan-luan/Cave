package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.EvalClock
import com.lomekwi.cave.pipeline.NodeRegistry
import com.lomekwi.cave.util.Units

/**
 * 验证时间节点的取值语义，输出当前求值时刻的秒值，区分片段内与时间轴两个端口。
 */
class TimeNodeTest extends munit.FunSuite {

  test("输出跟随求值上下文") {
    val node = new TimeNode()
    assertEquals(node.segmentOut.getType, classOf[Double])
    assertEquals(node.timelineOut.getType, classOf[Double])

    EvalClock.withTime(5 * Units.SECOND, 2 * Units.SECOND) {
      assertEquals(node.timelineOut.getData, 5.0)
      assertEquals(node.segmentOut.getData, 2.0)
    }

    EvalClock.withTime(7 * Units.SECOND, 3 * Units.SECOND) {
      assertEquals(node.timelineOut.getData, 7.0)
      assertEquals(node.segmentOut.getData, 3.0)
    }
  }

  test("嵌套求值后上下文恢复") {
    EvalClock.withTime(10 * Units.SECOND, 4 * Units.SECOND) {
      EvalClock.withTime(20 * Units.SECOND, 8 * Units.SECOND) {
        assertEquals(new TimeNode().timelineOut.getData, 20.0)
      }
      assertEquals(new TimeNode().timelineOut.getData, 10.0)
      assertEquals(new TimeNode().segmentOut.getData, 4.0)
    }
  }

  test("求值上下文之外访问输出抛出异常") {
    val node = new TimeNode()
    intercept[IllegalStateException] { node.segmentOut.getData }
    intercept[IllegalStateException] { node.timelineOut.getData }
  }

  test("已注册到节点注册表") {
    val registry = new NodeRegistry()
    val registered = (0 until registry.getCount).exists(i => registry.create(i).isInstanceOf[TimeNode])
    assert(registered, "时间节点应已注册")
  }
}
