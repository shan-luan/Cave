package com.lomekwi.cave.timeline

import com.lomekwi.cave.util.Duplicatable
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

import java.io.Serializable

import scala.collection.mutable

@SerialVersionUID(1L)
class Timeline(final val tracks: Vector[Track], final val groups: Vector[SegmentGroup]) extends Serializable with Iterable[Track] with Duplicatable[Timeline] {

  private def grownTo(ts: Vector[Track], index: Int): Vector[Track] = {
    var out = ts
    while (out.size <= index) {
      out = out :+ Track(out.size)
    }
    out
  }

  private[cave] def withTrack(index: Int, track: Track): Timeline = withTracks(Seq(index -> track))

  private[cave] def withTracks(changes: Seq[(Int, Track)]): Timeline = {
    if (changes.isEmpty) return this
    var ts = tracks
    for ((i, t) <- changes) {
      ts = grownTo(ts, i)
      ts = ts.updated(i, t)
    }
    new Timeline(ts, groups)
  }

  private def ensureTrack(index: Int): Timeline = {
    if (tracks.size > index) this else new Timeline(grownTo(tracks, index), groups)
  }

  def tryAdd(track: Track, segment: Content, interval: Interval, origin: Long): (Timeline, Long) = {
    val grown = ensureTrack(track.index)
    val current = grown.tracks(track.index)
    val (next, shift) = current.tryAdd(segment, interval, origin)
    if (shift == 0) (grown.withTrack(track.index, next), 0L) else (this, shift)
  }

  protected[timeline] def addOrThrow(track: Track, segment: Content, interval: Interval, origin: Long): Timeline = {
    val grown = ensureTrack(track.index)
    val current = grown.tracks(track.index)
    val next = current.addOrThrow(segment, interval, origin)
    grown.withTrack(track.index, next)
  }

  def remove(segment: Segment): Timeline = {
    findTrackOf(segment) match {
      case None => this
      case Some(track) =>
        var t: Timeline = withTrack(track.index, track.remove(segment))
        getGroup(segment).foreach { g => t = t.replaceGroup(g, g.remove(segment)) }
        t
    }
  }

  def remove(segments: Iterable[Segment]): Timeline = {
    val working = mutable.HashMap.empty[Int, Track]
    def currentOf(i: Int): Track = working.getOrElse(i, tracks(i))
    var t: Timeline = this
    for (s <- segments) {
      findTrackOf(s).foreach { track =>
        val before = currentOf(track.index)
        working.put(track.index, before.remove(s))
        for (g <- t.getGroup(s)) {
          t = t.replaceGroup(g, g.remove(s))
        }
      }
    }
    if (working.isEmpty) t else t.withTracks(working.toSeq)
  }

  def split(track: Track, time: Long): Timeline = {
    val current = getTrackOrCreate(track.index)
    if (current.canSplit(time)) withTrack(track.index, current.split(time)) else this
  }

  def setStart(segments: Iterable[Segment], deltaTime: Long): (Timeline, Long) = {
    applyPerTrack(segments, deltaTime, false)
  }

  def setEnd(segments: Iterable[Segment], deltaTime: Long): (Timeline, Long) = {
    applyPerTrack(segments, deltaTime, true)
  }

  private def applyPerTrack(segments: Iterable[Segment], deltaTime: Long, end: Boolean): (Timeline, Long) = {
    if (deltaTime == 0 || segments.isEmpty) return (this, 0L)
    val forward = deltaTime > 0
    val indices = trackIndicesOf(segments)
    if (indices.isEmpty) return (this, 0L)

    val bounds = indices
      .map((i: Int) => { val t = tracks(i); if (end) t.endLimit(segments, forward) else t.startLimit(segments, forward) })
    val bound: Long = if (forward) bounds.min else bounds.max
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return (this, 0L)

    val edits = mutable.ArrayBuffer.empty[(Int, Track)]
    for (i <- indices) {
      val before = tracks(i)
      val after = if (end) before.setEnd(segments, applied) else before.setStart(segments, applied)
      edits += (i -> after)
    }
    (withTracks(edits.toSeq), applied)
  }

  def moveTime(segments: Iterable[Segment], deltaTime: Long): (Timeline, Long) = {
    if (deltaTime == 0 || segments.isEmpty) return (this, 0L)
    val forward = deltaTime > 0
    val indices = trackIndicesOf(segments)
    if (indices.isEmpty) return (this, 0L)

    val bounds = indices.map((i: Int) => tracks(i).moveLimit(segments, forward))
    val bound: Long = if (forward) bounds.min else bounds.max
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return (this, 0L)

    val edits = mutable.ArrayBuffer.empty[(Int, Track)]
    for (i <- indices) {
      val before = tracks(i)
      // 内容先全部摘掉，再按新位置放回。转场是重叠区的派生物，随内容自动重建；
      // 只有两侧内容都不在本次搬运范围内时，才需要把转场本身当作搬运动作
      val onTrack = mutable.ArrayBuffer.empty[Segment]
      for (s <- segments) {
        if (before.contains(s)) onTrack += s
      }
      var next = before
      for (s <- onTrack) {
        s match {
          case c: Content => next = next.remove(c)
          case _: Transition =>
        }
      }
      // 内容自右向左放回：放每个内容时它的右邻居已经就位，右侧原有的转场对象才接得上。
      // 从左往右的话右邻居还在缺席，转场会被当成不再重叠而摘掉，放回后只能新建一个
      val refill = mutable.ArrayBuffer.empty[Content]
      for (s <- onTrack) {
        s match {
          case c: Content => refill += c
          case _: Transition =>
        }
      }
      refill.sortInPlaceWith((a: Content, b: Content) => before.getInterval(a).lo > before.getInterval(b).lo)
      for (c <- refill) {
        next = next.addOrThrow(c, before.getInterval(c).shift(applied), before.getOrigin(c) + applied)
      }
      for (s <- onTrack) {
        s match {
          case t: Transition =>
            before.transitionSides(t) match {
              case Some((left, right)) if !segments.exists(_ eq left) && !segments.exists(_ eq right) =>
                next = next.shiftTransition(t, applied)
              case _ =>
            }
          case _ =>
        }
      }
      edits += (i -> next)
    }
    (withTracks(edits.toSeq), applied)
  }

  /**
   * 仅按轨道索引平移片段（时间区间不变）。deltaTrack 截断到最大可用量后应用，保持组内成员相对间距。
   * @return 实际应用的轨道偏移；0 表示该方向无法移动，保持原位。
   */
  def moveTrack(segments: Iterable[Segment], deltaTrack: Int): (Timeline, Int) = {
    // 转场不允许换轨
    if (segments.exists {
      case _: Transition => true
      case _ => false
    }) {
      return (this, 0)
    }
    val applied = findPlaceableTrack(segments, deltaTrack)
    if (applied == 0) return (this, 0)

    val moves = mutable.ArrayBuffer.empty[(Content, Int, Int, Interval, Long)]
    for (s <- segments) {
      findTrackOf(s).foreach { from =>
        // 入口已排除转场
        val c = s.asInstanceOf[Content]
        val to = getTrackOrCreate(from.index + applied)
        moves += ((c, from.index, to.index, from.getInterval(c), from.getOrigin(c)))
      }
    }
    if (moves.isEmpty) return (this, 0)

    var base = tracks
    for ((_, _, toIndex, _, _) <- moves) {
      base = grownTo(base, toIndex)
    }
    val working = mutable.HashMap.empty[Int, Track]
    def currentOf(i: Int): Track = working.getOrElse(i, base(i))
    val beforeOf = mutable.HashMap.empty[Int, Track]
    for ((_, fromIndex, toIndex, _, _) <- moves) {
      beforeOf.getOrElseUpdate(fromIndex, base(fromIndex))
      beforeOf.getOrElseUpdate(toIndex, base(toIndex))
    }
    // 先全部摘除再全部放回。片段可能在成员之间换轨，摘除不完全会让放置被自己挡住
    for ((s, fromIndex, _, _, _) <- moves) {
      working.put(fromIndex, currentOf(fromIndex).remove(s))
    }
    for ((s, _, toIndex, interval, origin) <- moves) {
      val before = currentOf(toIndex)
      working.put(toIndex, before.addOrThrow(s, interval, origin))
    }

    var out = base
    for ((i, _) <- beforeOf) {
      out = out.updated(i, currentOf(i))
    }
    (new Timeline(out, groups), applied)
  }

  /** 涉及到的轨道索引，按出现顺序去重。 */
  private def trackIndicesOf(segments: Iterable[Segment]): mutable.LinkedHashSet[Int] = {
    val indices = mutable.LinkedHashSet.empty[Int]
    for (s <- segments) {
      findTrackOf(s).foreach(t => indices.add(t.index))
    }
    indices
  }

  /** 成员所在的轨道；要求成员在时间线上，否则抛 IllegalArgumentException。 */
  private def requiredTrack(segment: Segment): Track =
    findTrackOf(segment).getOrElse(throw new IllegalArgumentException(("片段不在时间线上: ".in(Zh) | "Segment is not on the timeline: ".in(En)).str() + segment))

  /** 在 deltaTrack 方向上找出整组可放置的最大轨道偏移（带符号，绝对值 ≤ |deltaTrack|）。 */
  private def findPlaceableTrack(segments: Iterable[Segment], deltaTrack: Int): Int = {
    if (deltaTrack == 0 || segments.isEmpty) return 0
    val minIdx: Int = segments.iterator.map((s: Segment) => requiredTrack(s).index).min
    val step = if (deltaTrack > 0) 1 else -1
    val span = Math.abs(deltaTrack)
    var k = span
    while (k > 0) {
      // 目标轨道尚不存在（索引 ≥ tracks.size）时视为空闲
      val target = minIdx + deltaTrack - step * (span - k)
      if (canPlaceGroupOnTrack(segments, target)) return step * k
      k -= 1
    }
    0
  }

  /** 整组按统一偏移移动后，是否每个成员在各自目标轨道上都不与既有片段冲突。 */
  private def canPlaceGroupOnTrack(segments: Iterable[Segment], target: Int): Boolean = {
    if (target < 0) {
      false
    } else {
      val moving = segments.toSet
      var refIdx = requiredTrack(segments.iterator.next()).index
      for (s <- segments) refIdx = Math.min(refIdx, requiredTrack(s).index)
      segments.forall { s =>
        val from = requiredTrack(s)
        val ti = from.index + (target - refIdx)
        // 目标轨道尚不存在（索引 ≥ tracks.size）时视为空闲。
        // 落点与既有内容重叠只要合法就允许，重叠会成为转场，不必强行换到空轨道
        ti >= tracks.size || (s match {
          case c: Content => tracks(ti).canPlaceAt(c, from.getInterval(s), moving)
          case _ => false
        })
      }
    }
  }

  /** 在 [time±threshold] 内扫描所有轨道条目，返回最近的起点/终点（无则原值）；ignore 不参与。 */
  def snapTime(time: Long, threshold: Long, ignore: scala.collection.Set[Segment]): Long = {
    var best = time
    var bestDist = threshold
    val searchStart: Long = Math.max(0, time - threshold)
    val searchEnd: Long = time + threshold
    if (searchEnd <= searchStart) return time
    val searchInterval: Interval = searchStart ~~ searchEnd
    for (track <- tracks) {
      for (s <- track.getIntersecting(searchInterval)) {
        if (!ignore.contains(s)) {
          val r = track.getInterval(s)
          var dist = Math.abs(r.lo - time)
          if (dist < bestDist) {
            best = r.lo
            bestDist = dist
          }
          dist = Math.abs(r.hi - time)
          if (dist < bestDist) {
            best = r.hi
            bestDist = dist
          }
        }
      }
    }
    if (time < threshold && time < bestDist) {
      best = 0
    }
    best
  }

  /** 持有该片段的轨道；未放置时返回 None。 */
  def findTrackOf(segment: Segment): Option[Track] =
    tracks.find(_.contains(segment))

  def newGroup(): (Timeline, SegmentGroup) = {
    val group = SegmentGroup(Set.empty)
    (new Timeline(tracks, groups :+ group), group)
  }

  def adoptGroup(group: SegmentGroup): Timeline = {
    if (groups.contains(group)) this else new Timeline(tracks, groups :+ group)
  }

  def dropGroup(group: SegmentGroup): Timeline = {
    new Timeline(tracks, groups.filterNot(_ == group))
  }

  def replaceGroup(old: SegmentGroup, next: SegmentGroup): Timeline = {
    val i = groups.indexOf(old)
    if (i < 0) this else new Timeline(tracks, groups.updated(i, next))
  }

  def getGroup(segment: Segment): Option[SegmentGroup] = {
    groups.find(_.contains(segment))
  }

  def getTrackOrCreate(index: Int): Track = {
    val ts = tracks
    if (index < ts.size) ts(index) else Track(index)
  }

  /** 已有轨道的条数。 */
  def getTrackCount: Int = tracks.size

  override def toString: String = {
    val body = tracks.zipWithIndex
      .map((track, i) => System.lineSeparator() + "track#" + i + ":" + track)
      .mkString
    "Timeline:" + body
  }
  def getLength: Long = {
    if (tracks.isEmpty) 0L else tracks.iterator.map(_.length).max
  }
  def getTracks: Seq[Track] = {
    tracks
  }

  /** 迭代有元素的轨道。 */
  override def iterator: Iterator[Track] = tracks.iterator.filterNot(_.isEmpty)

}

object Timeline {
  def empty: Timeline = new Timeline(Vector.empty[Track], Vector.empty[SegmentGroup])
}
