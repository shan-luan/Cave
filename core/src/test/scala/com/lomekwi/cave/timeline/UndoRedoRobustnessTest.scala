package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.TestProject

import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test

import java.util.{ArrayList, List, Random, Set}

import scala.jdk.CollectionConverters.*
import scala.util.Using

import UndoRedoRobustnessTest.*

/**
 * 时间线撤销/重做系统的鲁棒性测试：
 * 新建时间线，随机生成大量模拟片段、随机组合为组，再随机执行各种"拖拽"（整体移动、
 * 头/尾裁切、分割、删除、新增）。在随机时刻对时间线做序列化快照，继续随机操作后
 * 把快照之后产生的全部命令撤销掉，断言撤销回来的时间线与快照相等（再重做一遍，
 * 断言与操作后的状态相等）。
 *
 * 依赖 {@link Timeline#equals} / {@link Track#equals} 做结构化比较：按轨道、按区间
 * 逐项比对片段（源类型/时长、origin、区间），不依赖对象身份，因此能直接和深拷贝的
 * 快照对比。
 */
class UndoRedoRobustnessTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: Timeline = null
  private final val groups: List[SegmentGroup] = new ArrayList[SegmentGroup]()

  @Test
  def undoAfterRandomDragsRestoresSnapshot(): Unit = {
    // 多个种子多轮运行，覆盖不同的随机布局与操作序列
    for (seed <- Array(20240101, 20240202, 20240303, 12345678, 9876554,999999,888888888,(0xcafebabe).toInt,0xabcdef)) {
      runScenario(seed)
    }
  }

  private def runScenario(seed: Int): Unit = {
    project = new TestProject()
    timeline = project.timeline
    groups.clear()
    val rnd = new Random(seed)

    // 1. 随机创建大量片段并摆放到不重叠的区间
    createRandomSegments(rnd)

    // 2. 随机把片段组合为组
    randomGrouping(rnd)

    // 3. 随机拖拽若干步（全部可撤销）
    val preSteps = 20 + rnd.nextInt(21)
    var i = 0
    while (i < preSteps) {
      randomDrag(rnd)
      i += 1
    }

    // 4. 在随机时刻做序列化快照。
    //    同时清空命令历史：快照之后每执行一步就压入一条命令，
    //    之后"撤销同样的步数"即等价于撤销快照之后的全部操作，
    //    避免跨快照边界时同类型命令在撤销栈顶合并而污染步数统计。
    val snapshot = timeline.duplicate()
    project.undoManager.clear()

    val postSteps = 5 + rnd.nextInt(16)
    i = 0
    while (i < postSteps) {
      randomDrag(rnd)
      i += 1
    }

    // 再拍一份快照，稍后用于验证 redo
    val afterOps = timeline.duplicate()

    // 5. 撤销快照之后执行的全部命令，时间线应回到快照状态
    var undoCalls = 0
    while (project.undoManager.canUndo) {
      project.undoManager.undo()
      undoCalls += 1
    }
    assertTrue("快照后至少应产生一条可撤销的命令", undoCalls > 0)
    assertEquals("撤销后应恢复到序列化快照的状态", snapshot, timeline)

    // 6. 重做全部命令，时间线应回到操作后的状态
    var redoCalls = 0
    while (project.undoManager.canRedo) {
      project.undoManager.redo()
      redoCalls += 1
    }
    assertEquals("重做后应恢复到操作后的状态", afterOps, timeline)
    assertEquals("撤销与重做的步数应一致", undoCalls, redoCalls)
  }

  // ---------------------------------------------------------------------
  // 随机布局
  // ---------------------------------------------------------------------

  private def createRandomSegments(rnd: Random): Unit = {
    val occupied: List[List[Interval]] = new ArrayList[List[Interval]]()
    var i = 0
    while (i < TRACK_COUNT) {
      occupied.add(new ArrayList[Interval]())
      i += 1
    }
    i = 0
    while (i < SEGMENT_COUNT) {
      val duration = UndoRedoRobustnessTest.MIN_DURATION + rnd.nextLong(UndoRedoRobustnessTest.MAX_DURATION - UndoRedoRobustnessTest.MIN_DURATION + 1)
      val trackIndex = rnd.nextInt(TRACK_COUNT)
      val range = pickFreeRange(rnd, occupied.get(trackIndex), duration)
      if (range == null) {
        // 该轨道放不下就跳过
      } else {
        val seg = new Segment(new TestSource(duration))
        seg.setOrigin(rnd.nextLong(UndoRedoRobustnessTest.SPAN * 2))
        timeline.tryAdd(timeline.getTrack(trackIndex), seg, range)
      }
      i += 1
    }
  }

  private def pickFreeRange(rnd: Random, occupied: List[Interval], duration: Long): Interval = {
    var attempt = 0
    while (attempt < 50) {
      val start = rnd.nextLong(UndoRedoRobustnessTest.SPAN - duration + 1)
      val range: Interval = Interval(start, start + duration)
      var free = true
      val it = occupied.iterator()
      while (it.hasNext && free) {
        val o = it.next()
        if (o.isConnected(range)) {
          free = false
        }
      }
      if (free) {
        occupied.add(range)
        return range
      }
      attempt += 1
    }
    null
  }

  private def randomGrouping(rnd: Random): Unit = {
    for (track <- timeline.getTracks.asScala) {
      for (seg <- track.asScala) {
        if (rnd.nextFloat() < 0.35f) {
          var group: SegmentGroup = null
          if (!groups.isEmpty && rnd.nextFloat() < 0.5f) {
            group = groups.get(rnd.nextInt(groups.size()))
          } else {
            group = new SegmentGroup()
            groups.add(group)
          }
          group.add(seg)
        }
      }
    }
  }

  // ---------------------------------------------------------------------
  // 随机拖拽（每次 = 一个 record 块 = 一条可撤销命令）
  // ---------------------------------------------------------------------

  private def randomDrag(rnd: Random): Unit = {
    var attempt = 0
    while (attempt < 40) {
      val before = project.currentVersion
      performRandomOp(rnd)
      if (project.currentVersion != before) return // 成功记录了一步
      attempt += 1
    }
    forceChange(rnd) // 兜底：保证一定有可撤销的命令
  }

  private def performRandomOp(rnd: Random): Unit = {
    val placed = placedSegments()
    val op = rnd.nextInt(7)
    op match {
      case 0 | 1 => moveOp(rnd, placed)
      case 2 => frontResizeOp(rnd, placed)
      case 3 => behindResizeOp(rnd, placed)
      case 4 => splitOp(rnd, placed)
      case 5 => removeOp(rnd, placed)
      case _ => addOp(rnd)
    }
  }

  /** 与 UI 一致：拖拽锚点片段时，其所在组的成员会一起被操作。 */
  private def dragMembers(segment: Segment): List[Segment] = {
    val group = segment.getGroup
    if (group != null) List.copyOf(group) else List.of(segment)
  }

  private def moveOp(rnd: Random, placed: List[Segment]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed.get(rnd.nextInt(placed.size())))
    var minIdx = Integer.MAX_VALUE
    for (m <- members.asScala) {
      minIdx = Math.min(minIdx, m.getTrack.index)
    }
    var trackDelta = rnd.nextInt(3) - 1 // -1..1
    if (minIdx + trackDelta < 0) trackDelta = 0
    val deltaTime = rnd.nextLong(UndoRedoRobustnessTest.SPAN / 2) - UndoRedoRobustnessTest.SPAN / 4
    Using.resource(timeline.record()) { h =>
      // 与 UI 一致：时间与轨道维度各自截断到最大可用偏移后应用
      timeline.moveTime(members, deltaTime)
      timeline.moveTrack(members, trackDelta)
    }
  }

  private def frontResizeOp(rnd: Random, placed: List[Segment]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed.get(rnd.nextInt(placed.size())))
    val delta = rnd.nextLong(UndoRedoRobustnessTest.MAX_DURATION) - UndoRedoRobustnessTest.MAX_DURATION / 2
    Using.resource(timeline.record()) { h =>
      timeline.setStart(members, delta)
    }
  }

  private def behindResizeOp(rnd: Random, placed: List[Segment]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed.get(rnd.nextInt(placed.size())))
    val delta = rnd.nextLong(UndoRedoRobustnessTest.MAX_DURATION) - UndoRedoRobustnessTest.MAX_DURATION / 2
    Using.resource(timeline.record()) { h =>
      timeline.setEnd(members, delta)
    }
  }

  private def splitOp(rnd: Random, placed: List[Segment]): Unit = {
    if (placed.isEmpty) return
    val s = placed.get(rnd.nextInt(placed.size()))
    val r = s.getRange
    val lo: Long = r.lo
    val hi: Long = r.hi
    if (hi - lo < 2) return
    val time = lo + 1 + rnd.nextLong(hi - lo - 1)
    Using.resource(timeline.record()) { h =>
      timeline.split(s.getTrack, time)
    }
  }

  private def removeOp(rnd: Random, placed: List[Segment]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed.get(rnd.nextInt(placed.size())))
    Using.resource(timeline.record()) { h =>
      timeline.remove(members)
    }
  }

  private def addOp(rnd: Random): Unit = {
    val duration = UndoRedoRobustnessTest.MIN_DURATION + rnd.nextLong(UndoRedoRobustnessTest.MAX_DURATION - UndoRedoRobustnessTest.MIN_DURATION + 1)
    val track = timeline.getTrack(rnd.nextInt(TRACK_COUNT))
    var attempt = 0
    while (attempt < 30) {
      val start = rnd.nextLong(Math.max(1, UndoRedoRobustnessTest.SPAN - duration))
      val range: Interval = Interval(start, start + duration)
      if (track.isFree(range, Set.of[Segment]())) {
        val seg = new Segment(new TestSource(duration))
        seg.setOrigin(rnd.nextLong(UndoRedoRobustnessTest.SPAN))
        Using.resource(timeline.record()) { h =>
          timeline.tryAdd(track, seg, range)
        }
        return
      }
      attempt += 1
    }
  }

  private def forceChange(rnd: Random): Unit = {
    val placed = placedSegments()
    if (placed.isEmpty) {
      addOp(rnd) // 全空时补一个片段；仍失败则放弃这一步
      return
    }
    val members = dragMembers(placed.get(rnd.nextInt(placed.size())))
    Using.resource(timeline.record()) { h =>
      timeline.remove(members)
    }
  }

  private def placedSegments(): List[Segment] = {
    val out: List[Segment] = new ArrayList[Segment]()
    for (track <- timeline.getTracks.asScala) {
      for (s <- track.asScala) {
        out.add(s)
      }
    }
    out
  }
}

object UndoRedoRobustnessTest {
  /** 时间轴总跨度（µs） */
  private final val SPAN: Long = 200_000
  private final val MIN_DURATION: Long = 1_000
  private final val MAX_DURATION: Long = 15_000
  private final val TRACK_COUNT: Int = 4
  private final val SEGMENT_COUNT: Int = 60
}
