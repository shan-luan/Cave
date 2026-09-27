package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.TestProject

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

import java.util.Random

import UndoRedoRobustnessTest.SOURCE_COUNT

/**
 * 时间线撤销/重做系统的鲁棒性测试。
 * 新建时间线，用 [[RandomTimelineFiller]] 随机生成大量模拟片段、随机组合为组，再随机执行
 * 各种“拖拽”（整体移动、头/尾裁切、分割、删除、新增）。在随机时刻对时间线做序列化快照，
 * 继续随机操作后把快照之后产生的全部命令撤销掉，断言撤销回来的时间线与快照相等
 * （再重做一遍，断言与操作后的状态相等）。
 *
 * 依赖 [[Timeline.equals]] / [[Track.equals]] 做结构化比较，按轨道、按区间
 * 逐项比对片段（类型/时长、origin、区间），不依赖对象身份，因此能直接和深拷贝的
 * 快照对比。
 */
class UndoRedoRobustnessTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: Timeline = null

  @Test
  def undoAfterRandomDragsRestoresSnapshot(): Unit = {
    // 多个种子多轮运行，覆盖不同的随机布局与操作序列
    for (seed <- Array(20240101, 20240202, 20240303, 12345678, 9876554, 999999, 888888888, (0xcafebabe).toInt, 0xabcdef)) {
      runScenario(seed)
    }
  }

  private def runScenario(seed: Int): Unit = {
    project = new TestProject()
    timeline = project.timeline
    val rnd = new Random(seed)
    val filler = new RandomTimelineFiller(timeline, rnd)

    filler.fill(SOURCE_COUNT)

    filler.group()

    val preSteps = 20 + rnd.nextInt(21)
    var i = 0
    while (i < preSteps) {
      filler.randomDrag()
      i += 1
    }

    // 清空命令历史，快照之后每执行一步就压入一条命令，
    // 之后"撤销同样的步数"即等价于撤销快照之后的全部操作，
    // 避免跨快照边界时同类型命令在撤销栈顶合并而污染步数统计。
    val snapshot = timeline.duplicate()
    project.undoManager.clear()

    val postSteps = 5 + rnd.nextInt(16)
    i = 0
    while (i < postSteps) {
      filler.randomDrag()
      i += 1
    }

    // 再拍一份快照，稍后用于验证 redo
    val afterOps = timeline.duplicate()

    var undoCalls = 0
    while (project.undoManager.canUndo) {
      project.undoManager.undo()
      undoCalls += 1
    }
    assertTrue(undoCalls > 0, "快照后至少应产生一条可撤销的命令")
    assertEquals(snapshot, timeline, "撤销后应恢复到序列化快照的状态")

    var redoCalls = 0
    while (project.undoManager.canRedo) {
      project.undoManager.redo()
      redoCalls += 1
    }
    assertEquals(afterOps, timeline, "重做后应恢复到操作后的状态")
    assertEquals(undoCalls, redoCalls, "撤销与重做的步数应一致")
  }
}

object UndoRedoRobustnessTest {
  /** 初始随机生成的片段数量 */
  private final val SOURCE_COUNT: Int = 60
}
