package com.lomekwi.cave.timeline

import java.util.Random

import scala.collection.mutable

/**
 * 随机构造时间线内容的测试引擎。
 *
 * @param timeline       被操作的时间线
 * @param rnd            随机数源
 * @param span           时间轴总跨度（µs）
 * @param trackCount     轨道数
 * @param minDuration    片段最小时长（µs）
 * @param maxDuration    片段最大时长（µs）
 * @param segmentFactory 按随机时长构造片段
 */
class RandomTimelineFiller(final val timeline: TestTimeline,
                           final val rnd: Random,
                           private val span: Long = 200_000L,
                           private val trackCount: Int = 4,
                           private val minDuration: Long = 1_000L,
                           private val maxDuration: Long = 15_000L,
                           private val segmentFactory: Long => Content = duration => new TestCont(duration)) {

  /** 组注册表。 */
  private final val groups: mutable.ArrayBuffer[SegmentGroup] = mutable.ArrayBuffer.empty

  /** 随机往各轨道塞入 count 个片段，时长与落点均随机。 */
  def fill(count: Int): Unit = {
    val occupied = mutable.ArrayBuffer.empty[mutable.ArrayBuffer[Interval]]
    var i = 0
    while (i < trackCount) {
      occupied += mutable.ArrayBuffer.empty[Interval]
      i += 1
    }
    i = 0
    while (i < count) {
      val duration = randomDuration()
      val trackIndex = rnd.nextInt(trackCount)
      val interval = pickFreeInterval(occupied(trackIndex), duration)
      if (interval == null) {
      } else {
        val segment = segmentFactory(duration)
        timeline.add(timeline.getTrackOrCreate(trackIndex), segment, interval, Instant.At(Timespan(rnd.nextLong(span * 2))))
      }
      i += 1
    }
  }

  /** 随机往一条随机轨道上放一个片段。 */
  def addOne(): Boolean = {
    val duration = randomDuration()
    val track = timeline.getTrackOrCreate(rnd.nextInt(trackCount))
    var attempt = 0
    while (attempt < 30) {
      val start = rnd.nextLong(Math.max(1, span - duration))
      val interval: Interval = Instant.At(Timespan(start)) ~~ Instant.At(Timespan(start + duration))
      if (track.isFree(interval, Set.empty)) {
        val segment = segmentFactory(duration)
        timeline.snapshot()
        timeline.add(track, segment, interval, Instant.At(Timespan(rnd.nextLong(span))))
        return true
      }
      attempt += 1
    }
    false
  }

  /**
   * 按概率把片段随机组合成组。
   */
  def group(probability: Float = 0.35f, reuseProbability: Float = 0.5f): Unit = {
    for (track <- timeline.getTracks) {
      for (id <- track) {
        if (rnd.nextFloat() < probability) {
          val s = track.segment(id)
          var reuse = -1
          if (!groups.isEmpty && rnd.nextFloat() < reuseProbability) {
            reuse = rnd.nextInt(groups.size)
          }
          if (reuse >= 0) {
            groups(reuse) = timeline.groupAdd(groups(reuse), s)
          } else {
            groups += timeline.groupAdd(timeline.newGroup(), s)
          }
        }
      }
    }
  }

  /**
   * 随机执行一次“拖拽”，保证提交出一条可撤销命令。
   */
  def randomDrag(): Unit = {
    var attempt = 0
    while (attempt < 40) {
      val before = timeline.session.version
      performRandomOp()
      if (timeline.session.version != before) {
        assertLayoutValid()
        return
      }
      attempt += 1
    }
    forceChange()
    assertLayoutValid()
  }

  private def assertLayoutValid(): Unit = {
    for (track <- timeline.getTracks) {
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

  /** 拖拽锚点片段时，其所在组的成员会一起被操作。 */
  private def dragMembers(segment: Segment): mutable.ArrayBuffer[Segment] = {
    timeline.getGroup(segment) match {
      case Some(group) => mutable.ArrayBuffer.from(group.flatMap(id => timeline.timeline.segment(id)))
      case None => mutable.ArrayBuffer(segment)
    }
  }

  private def moveOp(placed: mutable.ArrayBuffer[Segment]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed(rnd.nextInt(placed.size)))
    var minIdx = Integer.MAX_VALUE
    for (m <- members) {
      timeline.findTrackOf(m).foreach(t => minIdx = Math.min(minIdx, t.index))
    }
    var trackDelta = rnd.nextInt(3) - 1
    if (minIdx + trackDelta < 0) trackDelta = 0
    val deltaTime = rnd.nextLong(span / 2) - span / 4
    timeline.snapshot()
    timeline.moveTime(members, Timespan(deltaTime))
    timeline.moveTrack(members, trackDelta)
  }

  private def frontResizeOp(placed: mutable.ArrayBuffer[Segment]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed(rnd.nextInt(placed.size)))
    val delta = rnd.nextLong(maxDuration) - maxDuration / 2
    timeline.snapshot()
    timeline.setStart(members, Timespan(delta))
  }

  private def behindResizeOp(placed: mutable.ArrayBuffer[Segment]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed(rnd.nextInt(placed.size)))
    val delta = rnd.nextLong(maxDuration) - maxDuration / 2
    timeline.snapshot()
    timeline.setEnd(members, Timespan(delta))
  }

  private def splitOp(placed: mutable.ArrayBuffer[Segment]): Unit = {
    if (placed.isEmpty) return
    val segment = placed(rnd.nextInt(placed.size))
    timeline.findTrackOf(segment).foreach { track =>
      val r = track.interval(segment)
      val lo: Instant = r.lo
      val hi: Instant = r.hi
      val len = hi - lo
      if (len > Timespan(1)) {
        val time = lo + Timespan(1) + Timespan(rnd.nextLong((len - Timespan(1)).value))
        timeline.snapshot()
        timeline.split(track, time)
      }
    }
  }

  private def removeOp(placed: mutable.ArrayBuffer[Segment]): Unit = {
    if (placed.isEmpty) return
    val members = dragMembers(placed(rnd.nextInt(placed.size)))
    timeline.snapshot()
    timeline.remove(members)
  }

  /** 兜底改动：删掉一个随机片段。 */
  private def forceChange(): Unit = {
    val placed = placedSegments()
    if (placed.isEmpty) {
      addOne()
      return
    }
    val members = dragMembers(placed(rnd.nextInt(placed.size)))
    timeline.snapshot()
    timeline.remove(members)
  }

  /** 在 [0, span] 内试探一个不与 occupied 相连的区间。 */
  private def pickFreeInterval(occupied: mutable.ArrayBuffer[Interval], duration: Long): Interval = {
    var attempt = 0
    while (attempt < 50) {
      val start = rnd.nextLong(span - duration + 1)
      val interval: Interval = Instant.At(Timespan(start)) ~~ Instant.At(Timespan(start + duration))
      var free = true
      val it = occupied.iterator
      while (it.hasNext && free) {
        if (it.next().isConnected(interval)) {
          free = false
        }
      }
      if (free) {
        occupied += interval
        return interval
      }
      attempt += 1
    }
    null
  }

  private def placedSegments(): mutable.ArrayBuffer[Segment] = {
    val out = mutable.ArrayBuffer.empty[Segment]
    for (track <- timeline.getTracks) {
      for (id <- track) {
        out += track.segment(id)
      }
    }
    out
  }

  private def randomDuration(): Long = minDuration + rnd.nextLong(maxDuration - minDuration + 1)
}
