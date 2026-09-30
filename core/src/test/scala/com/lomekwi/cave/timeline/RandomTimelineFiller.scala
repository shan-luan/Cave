package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.Segment

import java.util.{ArrayList, List, Random, Set}

import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * 随机构造时间线内容的测试引擎。随机落片段、随机组合成组、随机执行各种“拖拽”
 * （整体移动、头/尾裁切、分割、删除、新增），供需要随机时间线场景的测试复用。
 * 调用方持有同一个 [[Random]] 实例，同一个种子即可完全复现场景。
 *
 * 各操作与 UI 上的一次操作对应，且各自作为一个 record 块提交，因此一步即一条可撤销命令。
 *
 * @param timeline       被操作的时间线，撤销栈取自 [[Timeline.project]]
 * @param rnd            随机数源，与调用方共用同一个实例
 * @param span           时间轴总跨度（µs），落点与移动偏移都在这个范围内取
 * @param trackCount     轨道数，落点随机落在 [0, trackCount) 上
 * @param minDuration    片段最小时长（µs）
 * @param maxDuration    片段最大时长（µs）
 * @param segmentFactory 按随机时长构造片段，默认 [[TestCont]]
 */
class RandomTimelineFiller(final val timeline: Timeline,
                           final val rnd: Random,
                           private val span: Long = 200_000L,
                           private val trackCount: Int = 4,
                           private val minDuration: Long = 1_000L,
                           private val maxDuration: Long = 15_000L,
                           private val segmentFactory: Long => Segment[?] = duration => new TestCont(duration)) {

  /** 组注册表。组跨轨道，放在引擎里才能在分组时并入已有的随机一个组。 */
  private final val groups: List[SegmentGroup] = new ArrayList[SegmentGroup]()

  /** 随机往各轨道塞入 count 个片段，时长与落点均随机；该轨道放不下就跳过。 */
  def fill(count: Int): Unit = {
    val occupied: List[List[Interval]] = new ArrayList[List[Interval]]()
    var i = 0
    while (i < trackCount) {
      occupied.add(new ArrayList[Interval]())
      i += 1
    }
    i = 0
    while (i < count) {
      val duration = randomDuration()
      val trackIndex = rnd.nextInt(trackCount)
      val range = pickFreeRange(occupied.get(trackIndex), duration)
      if (range == null) {
        // 该轨道放不下就跳过
      } else {
        val segment = segmentFactory(duration)
        timeline.tryAdd(timeline.getTrackOrCreate(trackIndex), segment, range, rnd.nextLong(span * 2))
      }
      i += 1
    }
  }

  /** 随机往一条随机轨道上放一个片段，30 次试探内找到空位才放置。 */
  def addOne(): Boolean = {
    val duration = randomDuration()
    val track = timeline.getTrackOrCreate(rnd.nextInt(trackCount))
    var attempt = 0
    while (attempt < 30) {
      val start = rnd.nextLong(Math.max(1, span - duration))
      val range: Interval = start ~~ (start + duration)
      if (track.isFree(range, Set.of[Segment[?]]())) {
        val segment = segmentFactory(duration)
        Using.resource(timeline.record()) { _ =>
          timeline.tryAdd(track, segment, range, rnd.nextLong(span))
        }
        return true
      }
      attempt += 1
    }
    false
  }

  /**
   * 按概率把片段随机组合成组。每个片段以 probability 被选中，选中后以 reuseProbability
   * 并入已有的随机一个组，否则新建一个组。
   */
  def group(probability: Float = 0.35f, reuseProbability: Float = 0.5f): Unit = {
    for (track <- timeline.getTracks.asScala) {
      for (s <- track.asScala) {
        if (rnd.nextFloat() < probability) {
          var target: SegmentGroup = null
          if (!groups.isEmpty && rnd.nextFloat() < reuseProbability) {
            target = groups.get(rnd.nextInt(groups.size()))
          } else {
            target = timeline.newGroup()
            groups.add(target)
          }
          target.add(s)
        }
      }
    }
  }

  /**
   * 随机执行一次“拖拽”，保证提交出一条可撤销命令；多次尝试都改动不了模型时兜底增删一个片段。
   * 每次改动后都校验所有轨道的布局不变量。
   */
  def randomDrag(): Unit = {
    var attempt = 0
    while (attempt < 40) {
      val before = timeline.project.currentVersion
      performRandomOp()
      if (timeline.project.currentVersion != before) {
        assertLayoutValid()
        return // 成功记录了一步
      }
      attempt += 1
    }
    forceChange()
    assertLayoutValid()
  }

  private def assertLayoutValid(): Unit = {
    for (track <- timeline.getTracks.asScala) {
      TrackLayout.assertValid(track)
    }
  }

  private def performRandomOp(): Unit = {
    val placed = placedSegments()
    rnd.nextInt(7) match {
      case 0 | 1 => moveOp(placed)
      case 2 => frontResizeOp(placed)
      case 3 => behindResizeOp(placed)
      case 4 => splitOp(placed)
      case 5 => removeOp(placed)
      case _ => addOne()
    }
  }

  /** 与 UI 一致，拖拽锚点片段时，其所在组的成员会一起被操作。 */
  private def dragMembers(segment: Segment[?]): List[Segment[?]] = {
    val group = timeline.getGroup(segment)
    if (group != null) List.copyOf(group) else List.of(segment)
  }

  private def moveOp(placed: List[Segment[?]]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed.get(rnd.nextInt(placed.size())))
    var minIdx = Integer.MAX_VALUE
    for (m <- members.asScala) {
      minIdx = Math.min(minIdx, timeline.findTrackOf(m).index)
    }
    var trackDelta = rnd.nextInt(3) - 1 // -1..1
    if (minIdx + trackDelta < 0) trackDelta = 0
    val deltaTime = rnd.nextLong(span / 2) - span / 4
    Using.resource(timeline.record()) { _ =>
      // 与 UI 一致，时间与轨道维度各自截断到最大可用偏移后应用
      timeline.moveTime(members, deltaTime)
      timeline.moveTrack(members, trackDelta)
    }
  }

  private def frontResizeOp(placed: List[Segment[?]]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed.get(rnd.nextInt(placed.size())))
    val delta = rnd.nextLong(maxDuration) - maxDuration / 2
    Using.resource(timeline.record()) { _ =>
      timeline.setStart(members, delta)
    }
  }

  private def behindResizeOp(placed: List[Segment[?]]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed.get(rnd.nextInt(placed.size())))
    val delta = rnd.nextLong(maxDuration) - maxDuration / 2
    Using.resource(timeline.record()) { _ =>
      timeline.setEnd(members, delta)
    }
  }

  private def splitOp(placed: List[Segment[?]]): Unit = {
    if (placed.isEmpty) return
    val segment = placed.get(rnd.nextInt(placed.size()))
    val track = timeline.findTrackOf(segment)
    val r = track.getRange(segment)
    val lo: Long = r.lo
    val hi: Long = r.hi
    if (hi - lo < 2) return
    val time = lo + 1 + rnd.nextLong(hi - lo - 1)
    Using.resource(timeline.record()) { _ =>
      timeline.split(track, time)
    }
  }

  private def removeOp(placed: List[Segment[?]]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed.get(rnd.nextInt(placed.size())))
    Using.resource(timeline.record()) { _ =>
      timeline.remove(members)
    }
  }

  /** 兜底改动：删掉一个随机片段；时间线为空时改为新增，保证后续步骤仍有东西可操作。 */
  private def forceChange(): Unit = {
    val placed = placedSegments()
    if (placed.isEmpty) {
      addOne() // 仍失败则放弃这一步
      return
    }
    val members = dragMembers(placed.get(rnd.nextInt(placed.size())))
    Using.resource(timeline.record()) { _ =>
      timeline.remove(members)
    }
  }

  /** 在 [0, span] 内试探一个不与 occupied 相连的区间，50 次内找不到返回 null。 */
  private def pickFreeRange(occupied: List[Interval], duration: Long): Interval = {
    var attempt = 0
    while (attempt < 50) {
      val start = rnd.nextLong(span - duration + 1)
      val range: Interval = start ~~ (start + duration)
      var free = true
      val it = occupied.iterator()
      while (it.hasNext && free) {
        if (it.next().isConnected(range)) {
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

  /** 当前时间线上已放置的全部片段。 */
  private def placedSegments(): List[Segment[?]] = {
    val out: List[Segment[?]] = new ArrayList[Segment[?]]()
    for (track <- timeline.getTracks.asScala) {
      for (s <- track.asScala) {
        out.add(s)
      }
    }
    out
  }

  private def randomDuration(): Long = minDuration + rnd.nextLong(maxDuration - minDuration + 1)
}
