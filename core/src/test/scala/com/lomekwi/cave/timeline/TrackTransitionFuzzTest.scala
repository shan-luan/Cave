package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Content, Segment, Transition}
import com.lomekwi.cave.project.TestProject

import org.junit.jupiter.api.Assertions.{assertNotNull, fail}
import org.junit.jupiter.api.Test

import java.util.Random

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/**
 * 转场删除的随机形态测试。反复删除转场并断言：内容不会被转场顶掉区间而从迭代里消失，
 * 且没有任何内容与转场占据同一个区间。
 *
 * 一半用随机堆叠的布局做广泛覆盖，一半用收紧的形态族随机生成：p 与 a 重叠、a 与 b 重叠、
 * 第二后继 rn 的起点在 p 的终点附近抖动，把“交界点被第二后继压回下界”的边界两侧都扫到。
 */
class TrackTransitionFuzzTest extends GdxTestBase {

  @Test
  def removingTransitionsOnRandomLayouts(): Unit = {
    for (seed <- 1 to 16) {
      runRandomLayout(seed)
    }
  }

  @Test
  def removingTransitionRandomNeighbourShapes(): Unit = {
    var i = 0
    while (i < 1200) {
      runNeighbourShape(i)
      i += 1
    }
  }

  private def runRandomLayout(seed: Int): Unit = {
    val timeline = new TestProject().timeline
    val rnd = new Random(seed)
    val known = mutable.ArrayBuffer.from(buildRandomTrack(timeline, rnd))

    var step = 0
    while (step < 400) {
      val track = timeline.getTrackOrCreate(0)
      val transitions = track.asScala.collect { case t: Transition[?] => t }.toVector
      if (transitions.nonEmpty && rnd.nextInt(4) != 0) {
        timeline.remove(transitions(rnd.nextInt(transitions.size)))
      } else {
        val added = tryAddRandom(timeline, rnd)
        if (added != null) known += added
      }
      assertConsistent(timeline, known.toVector, "seed=" + seed + " step=" + step)
      step += 1
    }
  }

  /** 用随机时长与随机落点堆叠片段；放得下才留下，因此每次得到的都是合法布局。 */
  private def buildRandomTrack(timeline: Timeline, rnd: Random): Vector[Segment[?]] = {
    val out = Vector.newBuilder[Segment[?]]
    var added = 0
    var attempts = 0
    while (added < 30 && attempts < 900) {
      attempts += 1
      val segment = tryAddRandom(timeline, rnd)
      if (segment != null) {
        out += segment
        added += 1
      }
    }
    out.result()
  }

  /** 试放一个随机片段，放得下返回它，否则返回 null。 */
  private def tryAddRandom(timeline: Timeline, rnd: Random): Segment[?] = {
    val duration = TrackTransitionFuzzTest.DURATION_MIN + rnd.nextLong(TrackTransitionFuzzTest.DURATION_MAX - TrackTransitionFuzzTest.DURATION_MIN + 1)
    val lo = rnd.nextLong(TrackTransitionFuzzTest.SPAN)
    val segment = new TestCont(duration)
    val shift = timeline.tryAdd(timeline.getTrackOrCreate(0), segment,
      lo ~~ (lo + duration), rnd.nextLong(TrackTransitionFuzzTest.SPAN))
    if (shift == 0) segment else null
  }

  /**
   * 生成一组 p、a、b、rn，各段时长、重叠量与 rn 的抖动都在随机范围内，
   * 然后删掉 a-b 转场并校验。rn 起点取 p 终点加 j，j 从 1 起，j=1 时交界点会被压回下界。
   */
  private def runNeighbourShape(iter: Int): Unit = {
    val rnd = new Random(iter * 2654435761L + 17)
    val timeline = new TestProject().timeline
    val origin = rnd.nextInt(20).toLong
    val pDur = 2L + rnd.nextInt(600)
    val o1 = 1L + rnd.nextInt(pDur.toInt - 1)
    val j = 1L + rnd.nextInt(6)
    val o2 = 1L + rnd.nextInt(j.toInt)
    val bDur = o2 + 1 + rnd.nextInt(400)
    val rDur = bDur + rnd.nextInt(200)

    val p = new TestCont(pDur)
    val a = new TestCont(o1 + o2)
    val b = new TestCont(bDur)
    val rn = new TestCont(rDur)
    val pStart = origin
    val aStart = pStart + pDur - o1
    val aEnd = aStart + o1 + o2
    val bStart = aEnd - o2
    val bEnd = bStart + bDur
    val rnStart = pStart + pDur + j
    val rnEnd = rnStart + rDur

    val tl = timeline
    tl.addOrThrow(tl.getTrackOrCreate(0), p, pStart ~~ (pStart + pDur), origin)
    tl.addOrThrow(tl.getTrackOrCreate(0), a, aStart ~~ aEnd, origin)
    tl.addOrThrow(tl.getTrackOrCreate(0), b, bStart ~~ bEnd, origin)
    tl.addOrThrow(tl.getTrackOrCreate(0), rn, rnStart ~~ rnEnd, origin)

    val track = tl.getTrackOrCreate(0)
    val transition = track.transitionBetween(a.asInstanceOf[Content[?]], b.asInstanceOf[Content[?]])
    assertNotNull(transition)
    tl.remove(transition)
    assertConsistent(tl, Vector(p, a, b, rn), "iter=" + iter)
  }

  private def assertConsistent(timeline: Timeline, known: Vector[Segment[?]], label: String): Unit = {
    for (track <- timeline.getTracks.asScala) {
      val visible = track.asScala.toVector

      // 反查还在却从迭代里消失，说明它被同键的转场顶掉了，这正是内容与转场同区间的表现形式
      for (s <- known) {
        if (track.contains(s) != visible.exists(_ eq s)) {
          fail("内容在轨道反查里存在却从迭代中消失 " + label + " track=" + track.index + " " + s)
        }
      }

      val transitions = visible.collect { case t: Transition[?] => t }
      for (c <- known if track.contains(c); t <- transitions) {
        if (track.getRange(c) == track.getRange(t)) {
          fail("内容与转场占据同一区间 " + label + " " + track.getRange(c))
        }
      }
    }
  }
}

object TrackTransitionFuzzTest {
  private final val SPAN = 20_000L
  private final val DURATION_MIN = 100L
  private final val DURATION_MAX = 3_000L
}
