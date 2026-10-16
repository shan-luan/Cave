package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.TestProject

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

import java.util.Random

import UndoRedoRobustnessTest.SOURCE_COUNT

/**
 * 时间线撤销/重做系统的鲁棒性测试：随机生成片段、随机组合并随机“拖拽”，
 * 校验撤销后回到序列化快照、重做后回到操作后的状态。
 *
 * 依赖 [[Timeline.equals]] / [[Track.equals]] 做结构化比较，按轨道、按区间
 * 逐项比对片段（类型/时长、origin、区间），不依赖对象身份，因此能直接和深拷贝的
 * 快照对比。
 */
class UndoRedoRobustnessTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: TestTimeline = null

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

    val snapshot = timeline.timeline.duplicate()
    project.undo.clear()

    val postSteps = 5 + rnd.nextInt(16)
    i = 0
    while (i < postSteps) {
      filler.randomDrag()
      i += 1
    }

    // 再拍一份快照，稍后用于验证 redo
    val afterOps = timeline.timeline.duplicate()

    var undoCalls = 0
    while (project.undo.canUndo) {
      project.undo.undo()
      undoCalls += 1
    }
    assertTrue(undoCalls > 0, "快照后至少应产生一条可撤销的命令")
    assertEquals(snapshot, timeline.timeline, "撤销后应恢复到序列化快照的状态")

    var redoCalls = 0
    while (project.undo.canRedo) {
      project.undo.redo()
      redoCalls += 1
    }
    assertEquals(afterOps, timeline.timeline, "重做后应恢复到操作后的状态")
    assertEquals(undoCalls, redoCalls, "撤销与重做的步数应一致")
  }
}

object UndoRedoRobustnessTest {
  /** 初始随机生成的片段数量 */
  private final val SOURCE_COUNT: Int = 60
}
