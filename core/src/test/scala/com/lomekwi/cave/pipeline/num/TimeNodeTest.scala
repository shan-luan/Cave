package com.lomekwi.cave.pipeline.num

import com.lomekwi.cave.pipeline.EvalClock
import com.lomekwi.cave.pipeline.NodeRegistry
import com.lomekwi.cave.util.Units
import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows, assertTrue}
import org.junit.jupiter.api.Test

/**
 * 验证时间节点的取值语义，输出当前求值时刻的秒值，区分片段内与时间轴两个端口。
 */
class TimeNodeTest {

  @Test
  def output_followsEvalContext(): Unit = {
    val node = new TimeNode()
    assertEquals(classOf[Double], node.segmentOut.getType)
    assertEquals(classOf[Double], node.timelineOut.getType)

    EvalClock.withTime(5 * Units.SECOND, 2 * Units.SECOND) {
      assertEquals(5.0, node.timelineOut.getData)
      assertEquals(2.0, node.segmentOut.getData)
    }

    EvalClock.withTime(7 * Units.SECOND, 3 * Units.SECOND) {
      assertEquals(7.0, node.timelineOut.getData)
      assertEquals(3.0, node.segmentOut.getData)
    }
  }

  @Test
  def context_restoredAfterNestedEvaluation(): Unit = {
    EvalClock.withTime(10 * Units.SECOND, 4 * Units.SECOND) {
      EvalClock.withTime(20 * Units.SECOND, 8 * Units.SECOND) {
        assertEquals(20.0, new TimeNode().timelineOut.getData)
      }
      assertEquals(10.0, new TimeNode().timelineOut.getData)
      assertEquals(4.0, new TimeNode().segmentOut.getData)
    }
  }

  @Test
  def output_throwsOutsideEvalContext(): Unit = {
    val node = new TimeNode()
    assertThrows(classOf[IllegalStateException], () => node.segmentOut.getData)
    assertThrows(classOf[IllegalStateException], () => node.timelineOut.getData)
  }

  @Test
  def registered_inNodeRegistry(): Unit = {
    val registry = new NodeRegistry()
    val registered = (0 until registry.getCount).exists(i => registry.create(i).isInstanceOf[TimeNode])
    assertTrue(registered, "时间节点应已注册")
  }
}
