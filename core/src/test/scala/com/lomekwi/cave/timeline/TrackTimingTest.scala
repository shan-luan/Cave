package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Content, Segment, Transition}
import com.lomekwi.cave.project.TestProject

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.util.{ArrayList, List}

import scala.jdk.CollectionConverters.*

/**
 * 大轨道上的单次编辑耗时。轨道上铺 [[TrackTimingTest.COUNT]] 条内容，相邻两条重叠出转场，
 * 共 COUNT-1 个转场，然后分别量一次单片段拖拽、一次成组拖拽、一次删除的墙钟耗时。
 *
 * 统计的是模型层调用，含命令对象的构造，与界面一次拖拽/一次删除对应。
 * 断言阈值给得很松，只挡灾难性回退（例如又变回每次改动都全表重算），
 * 真正的信号是打印出来的数字。耗时随机器与 JIT 状态波动，别拿它当精确基准。
 */
class TrackTimingTest extends GdxTestBase {

  private var project: TestProject = null
  private var timeline: Timeline = null

  @BeforeEach
  def setUp(): Unit = {
    project = new TestProject()
    timeline = project.timeline
  }

  @Test
  def editCostOnThousandEntryTrack(): Unit = {
    val track = timeline.getTrackOrCreate(0)
    val segments = new ArrayList[Segment[?]](TrackTimingTest.COUNT)
    var i = 0
    while (i < TrackTimingTest.COUNT) {
      val lo = i.toLong * TrackTimingTest.STEP
      val segment = new TestCont(TrackTimingTest.SOURCE_LENGTH)
      timeline.addOrThrow(track, segment, lo ~~ (lo + TrackTimingTest.SPAN), 0L)
      segments.add(segment)
      i += 1
    }

    val filled = timeline.getTrackOrCreate(0)
    // 夹具自检：COUNT 条内容两两相邻重叠，正好 COUNT-1 个转场
    assertEquals(TrackTimingTest.COUNT, filled.asScala.count(_.isInstanceOf[Content[?]]))
    assertEquals(TrackTimingTest.COUNT - 1, filled.asScala.count(_.isInstanceOf[Transition[?]]))
    println("[timing] 轨道条目 " + filled.asScala.size + "，内容 " + TrackTimingTest.COUNT +
      "，转场 " + (TrackTimingTest.COUNT - 1) + "，预热 " + TrackTimingTest.WARMUP +
      " 次，重复 " + TrackTimingTest.REPEATS + " 次")

    val single: List[Segment[?]] = List.of(segments.get(TrackTimingTest.COUNT / 2))
    val members: List[Segment[?]] =
      List.copyOf(segments.subList(TrackTimingTest.GROUP_AT, TrackTimingTest.GROUP_AT + TrackTimingTest.GROUP))

    // 与界面把一个组整体拖动一致，成员里含组内相邻内容之间的转场
    val grouped: List[Segment[?]] = withInternalTransitions(timeline.getTrackOrCreate(0), members)
    assertEquals(2 * TrackTimingTest.GROUP - 1, grouped.size(), "组成员应含组内转场")

    // 先确认三种拖拽真的会移动，否则量到的是空转
    assertEquals(TrackTimingTest.DELTA, timeline.moveTime(single, TrackTimingTest.DELTA), "单片段拖拽应移动")
    assertEquals(-TrackTimingTest.DELTA, timeline.moveTime(single, -TrackTimingTest.DELTA), "单片段拖拽应移回")
    assertEquals(TrackTimingTest.DELTA, timeline.moveTime(members, TrackTimingTest.DELTA), "成组拖拽应移动")
    assertEquals(-TrackTimingTest.DELTA, timeline.moveTime(members, -TrackTimingTest.DELTA), "成组拖拽应移回")
    assertEquals(TrackTimingTest.DELTA, timeline.moveTime(grouped, TrackTimingTest.DELTA), "含转场的成组拖拽应移动")
    assertEquals(-TrackTimingTest.DELTA, timeline.moveTime(grouped, -TrackTimingTest.DELTA), "含转场的成组拖拽应移回")

    val victim = segments.get(TrackTimingTest.VICTIM_AT)
    val victimRange = timeline.getTrackOrCreate(0).getRange(victim)
    val victimOrigin = timeline.getTrackOrCreate(0).getOrigin(victim)
    timeline.remove(victim)
    assertFalse(timeline.getTrackOrCreate(0).contains(victim), "删除后片段应离开轨道")
    timeline.addOrThrow(track, victim, victimRange, victimOrigin)
    assertTrue(timeline.getTrackOrCreate(0).contains(victim), "放回后片段应回到轨道")

    // 拖拽正反各一次为一次往返，取一半作为单次耗时
    val (singleMin, singleAvg) = time {
      timeline.moveTime(single, TrackTimingTest.DELTA)
      timeline.moveTime(single, -TrackTimingTest.DELTA)
    }
    val (groupMin, groupAvg) = time {
      timeline.moveTime(members, TrackTimingTest.DELTA)
      timeline.moveTime(members, -TrackTimingTest.DELTA)
    }
    // 组内转场在平移后保持对象身份，所以像界面那样一次取好的成员表在整轮计时里都有效
    val (groupedMin, groupedAvg) = time {
      timeline.moveTime(grouped, TrackTimingTest.DELTA)
      timeline.moveTime(grouped, -TrackTimingTest.DELTA)
    }
    assertTrue(grouped.asScala.forall((s: Segment[?]) => timeline.getTrackOrCreate(0).contains(s)),
      "计时后成员表应仍然全部在轨道上")
    val (removeMin, removeAvg) = time({
      timeline.remove(victim)
    }, {
      timeline.addOrThrow(track, victim, victimRange, victimOrigin)
    })

    // 拖拽路径里每个转场成员都要问一次它的两侧，这里把全轨道的转场问一遍，单独看这一步的代价
    val dense = timeline.getTrackOrCreate(0)
    val allTransitions = dense.asScala.collect { case t: Transition[?] => t }.toVector
    assertEquals(TrackTimingTest.COUNT - 1, allTransitions.size)
    val (sidesMin, sidesAvg) = time {
      var k = 0
      while (k < allTransitions.size) {
        dense.transitionSides(allTransitions(k))
        k += 1
      }
    }

    report("单片段拖拽", singleMin / 2, singleAvg / 2)
    report("成组拖拽 " + TrackTimingTest.GROUP + " 条内容", groupMin / 2, groupAvg / 2)
    report("成组拖拽 " + TrackTimingTest.GROUP + " 条内容 + " + (TrackTimingTest.GROUP - 1) + " 个组内转场", groupedMin / 2, groupedAvg / 2)
    report("删除一条内容", removeMin, removeAvg)
    report("问遍 " + allTransitions.size + " 个转场的两侧", sidesMin, sidesAvg)

    assertTrue(singleMin < 1_000_000L, "单片段拖拽耗时异常: " + singleMin + "ns")
    assertTrue(groupMin < 10_000_000L, "成组拖拽耗时异常: " + groupMin + "ns")
    assertTrue(groupedMin < 10_000_000L, "含转场的成组拖拽耗时异常: " + groupedMin + "ns")
    assertTrue(removeMin < 1_000_000L, "删除耗时异常: " + removeMin + "ns")
  }

  /** 在每个内容之后插入它与下一个内容之间的转场，得到界面拖一个组时那样的成员表。 */
  private def withInternalTransitions(track: Track, contents: List[Segment[?]]): List[Segment[?]] = {
    val out = new ArrayList[Segment[?]](2 * contents.size())
    var i = 0
    while (i < contents.size()) {
      val content = contents.get(i).asInstanceOf[Content[?]]
      out.add(content)
      if (i + 1 < contents.size()) {
        val transition = track.transitionAfter(content)
        if (transition != null) out.add(transition)
      }
      i += 1
    }
    out
  }

  /** 预热后重复 [[TrackTimingTest.REPEATS]] 次 op，返回单次的（最快, 平均）耗时。 */
  private def time(op: => Unit): (Long, Long) = time(op, ())

  /**
   * 同上，但每次统计之后执行 reset，用于让被测操作可重复，它的耗时不计入。
   * 取最快值是为了躲开 GC 停顿这类噪声。
   */
  private def time(op: => Unit, reset: => Unit): (Long, Long) = {
    var i = 0
    while (i < TrackTimingTest.WARMUP) {
      op
      reset
      i += 1
    }
    var best = Long.MaxValue
    var total = 0L
    i = 0
    while (i < TrackTimingTest.REPEATS) {
      val start = System.nanoTime()
      op
      val cost = System.nanoTime() - start
      if (cost < best) best = cost
      total += cost
      reset
      i += 1
    }
    (best, total / TrackTimingTest.REPEATS)
  }

  private def report(label: String, minNs: Long, avgNs: Long): Unit = {
    println("[timing] " + label + " 最快 " + (minNs / 1000) + " µs，平均 " + (avgNs / 1000) + " µs")
  }
}

object TrackTimingTest {
  /** 轨道上的内容条数 */
  private final val COUNT = 1000
  /** 相邻内容的起点间距，与 [[TrackTimingTest.SPAN]] 配合让每对相邻内容都重叠出转场 */
  private final val STEP = 2000L
  /** 单条内容的区间长度，比 STEP 长，于是每对相邻内容重叠 SPAN-STEP，隔项之间还留着 STEP 的空当 */
  private final val SPAN = 3000L
  /** 内容源的时长，取得足够大，免得移动时被素材终点夹住 */
  private final val SOURCE_LENGTH = 10_000_000L
  /** 单次拖拽的偏移量，小于几何允许的上限 */
  private final val DELTA = 500L
  /** 成组拖拽的成员条数 */
  private final val GROUP = 100
  /** 成组拖拽的起始下标 */
  private final val GROUP_AT = 400
  /** 删除用例的目标下标 */
  private final val VICTIM_AT = 600
  /** 不计时的预热次数 */
  private final val WARMUP = 200
  /** 计时的重复次数 */
  private final val REPEATS = 200
}
