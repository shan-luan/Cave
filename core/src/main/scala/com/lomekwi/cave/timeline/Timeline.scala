package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.{Id, IdSource}
import com.lomekwi.cave.util.Duplicatable
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import java.io.Serializable

import scala.collection.mutable

@SerialVersionUID(1L)
case class Timeline(tracks: Vector[Track], groups: Vector[SegmentGroup]) extends Serializable with Iterable[Track] with Duplicatable[Timeline] {

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
    Timeline(ts, groups)
  }

  private def ensureTrack(index: Int): Timeline = {
    if (tracks.size > index) this else Timeline(grownTo(tracks, index), groups)
  }
  def add(trackIndex: Int, segment: Content, interval: Interval, origin: Instant)(using ids: IdSource): Timeline = {
    val grown = ensureTrack(trackIndex)
    val current = grown.tracks(trackIndex)
    val next = current.add(segment, interval, origin)
    if (next eq current) this else grown.withTrack(trackIndex, next)
  }

  def remove(id: Id)(using ids: IdSource): Timeline = {
    findTrackOf(id) match {
      case None => this
      case Some(track) =>
        var t: Timeline = withTrack(track.index, track.remove(id))
        getGroup(id).foreach { g => t = t.replaceGroup(g, g.remove(id)) }
        t
    }
  }

  def remove(ids: Iterable[Id])(using source: IdSource): Timeline = {
    val working = mutable.HashMap.empty[Int, Track]
    def currentOf(i: Int): Track = working.getOrElse(i, tracks(i))
    var t: Timeline = this
    for (id <- ids) {
      findTrackOf(id).foreach { track =>
        val before = currentOf(track.index)
        working.put(track.index, before.remove(id))
        for (g <- t.getGroup(id)) {
          t = t.replaceGroup(g, g.remove(id))
        }
      }
    }
    if (working.isEmpty) t else t.withTracks(working.toSeq)
  }

  def split(track: Track, time: Instant): Timeline = {
    val current = getTrackOrCreate(track.index)
    if (current.canSplit(time)) withTrack(track.index, current.split(time)) else this
  }

  def setStart(ids: Iterable[Id], deltaTime: Timespan)(using source: IdSource): (Timeline, Timespan) = {
    applyPerTrack(ids, deltaTime, false)
  }

  def setEnd(ids: Iterable[Id], deltaTime: Timespan)(using source: IdSource): (Timeline, Timespan) = {
    applyPerTrack(ids, deltaTime, true)
  }

  private def applyPerTrack(ids: Iterable[Id], deltaTime: Timespan, end: Boolean)(using source: IdSource): (Timeline, Timespan) = {
    if (deltaTime == Timespan.Zero || ids.isEmpty) return (this, Timespan.Zero)
    val forward = deltaTime > Timespan.Zero
    val indices = trackIndicesOf(ids)
    if (indices.isEmpty) return (this, Timespan.Zero)

    val bounds = indices
      .map((i: Int) => { val t = tracks(i); if (end) t.endLimit(ids, forward) else t.startLimit(ids, forward) })
    val bound: Timespan = if (forward) bounds.min else bounds.max
    val applied = if (forward) deltaTime.min(bound.max(Timespan.Zero))
                  else deltaTime.max(bound.min(Timespan.Zero))
    if (applied == Timespan.Zero) return (this, Timespan.Zero)

    val edits = mutable.ArrayBuffer.empty[(Int, Track)]
    for (i <- indices) {
      val before = tracks(i)
      val after = if (end) before.setEnd(ids, applied) else before.setStart(ids, applied)
      edits += (i -> after)
    }
    (withTracks(edits.toSeq), applied)
  }

  def moveTime(ids: Iterable[Id], deltaTime: Timespan)(using source: IdSource): (Timeline, Timespan) = {
    if (deltaTime == Timespan.Zero || ids.isEmpty) return (this, Timespan.Zero)
    val forward = deltaTime > Timespan.Zero
    val indices = trackIndicesOf(ids)
    if (indices.isEmpty) return (this, Timespan.Zero)

    val bounds = indices.map((i: Int) => tracks(i).moveLimit(ids, forward))
    val bound: Timespan = if (forward) bounds.min else bounds.max
    val applied = if (forward) deltaTime.min(bound.max(Timespan.Zero))
                  else deltaTime.max(bound.min(Timespan.Zero))
    if (applied == Timespan.Zero) return (this, Timespan.Zero)

    val idsVector = ids.toVector
    val idSet = idsVector.toSet
    val edits = mutable.ArrayBuffer.empty[(Int, Track)]
    for (i <- indices) {
      val before = tracks(i)
      val onTrack = idsVector.filter(id => before.contains(id)).flatMap(id => before.segmentOption(id))
      var next = before
      for (s <- onTrack) {
        s match {
          case c: Content => next = next.remove(c.id)
          case _: Transition =>
        }
      }
      val refill = mutable.ArrayBuffer.empty[Content]
      for (s <- onTrack) {
        s match {
          case c: Content => refill += c
          case _: Transition =>
        }
      }
      refill.sortInPlaceWith((a: Content, b: Content) => before.interval(a.id).lo > before.interval(b.id).lo)
      for (c <- refill) {
        next = next.add(c, before.interval(c.id).shift(applied), before.getOrigin(c.id) + applied)
      }
      for (s <- onTrack) {
        s match {
          case t: Transition =>
            before.transitionSides(t) match {
              case Some((left, right)) if !idSet.contains(left.id) && !idSet.contains(right.id) =>
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
   * 仅按轨道索引平移片段（时间区间不变）。
   * @return 实际应用的轨道偏移。
   */
  def moveTrack(ids: Iterable[Id], deltaTrack: Int)(using source: IdSource): (Timeline, Int) = {
    val idsVector = ids.toVector
    if (idsVector.exists(id => segment(id).exists(_.isInstanceOf[Transition]))) {
      return (this, 0)
    }
    val applied = findPlaceableTrack(idsVector, deltaTrack)
    if (applied == 0) return (this, 0)

    val moves = mutable.ArrayBuffer.empty[(Content, Int, Int, Interval, Instant)]
    for (id <- idsVector) {
      findTrackOf(id).foreach { from =>
        val c = from.segment(id).asInstanceOf[Content]
        val to = getTrackOrCreate(from.index + applied)
        moves += ((c, from.index, to.index, from.interval(id), from.getOrigin(id)))
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
    for ((s, fromIndex, _, _, _) <- moves) {
      working.put(fromIndex, currentOf(fromIndex).remove(s.id))
    }
    for ((s, _, toIndex, interval, origin) <- moves) {
      val before = currentOf(toIndex)
      working.put(toIndex, before.add(s, interval, origin))
    }

    var out = base
    for ((i, _) <- beforeOf) {
      out = out.updated(i, currentOf(i))
    }
    (Timeline(out, groups), applied)
  }

  /** 涉及到的轨道索引，按出现顺序去重。 */
  private def trackIndicesOf(ids: Iterable[Id]): mutable.LinkedHashSet[Int] = {
    val indices = mutable.LinkedHashSet.empty[Int]
    for (id <- ids) {
      findTrackOf(id).foreach(t => indices.add(t.index))
    }
    indices
  }

  /** 成员所在的轨道。 */
  private def requiredTrack(id: Id): Track =
    findTrackOf(id).getOrElse(throw new IllegalArgumentException(("片段不在时间线上: ".in(Zh) | "Segment is not on the timeline: ".in(En)).str() + id))

  /** 在 deltaTrack 方向上找出整组可放置的最大轨道偏移。 */
  private def findPlaceableTrack(ids: Iterable[Id], deltaTrack: Int): Int = {
    if (deltaTrack == 0 || ids.isEmpty) return 0
    val minIdx: Int = ids.iterator.map((id: Id) => requiredTrack(id).index).min
    val step = if (deltaTrack > 0) 1 else -1
    val span = Math.abs(deltaTrack)
    var k = span
    while (k > 0) {
      val target = minIdx + deltaTrack - step * (span - k)
      if (canPlaceGroupOnTrack(ids, target)) return step * k
      k -= 1
    }
    0
  }

  /** 整组按统一偏移移动后，是否每个成员在各自目标轨道上都不与既有片段冲突。 */
  private def canPlaceGroupOnTrack(ids: Iterable[Id], target: Int): Boolean = {
    if (target < 0) {
      false
    } else {
      val moving = ids.toSet
      var refIdx = requiredTrack(ids.iterator.next()).index
      for (id <- ids) refIdx = Math.min(refIdx, requiredTrack(id).index)
      ids.forall { id =>
        val from = requiredTrack(id)
        val ti = from.index + (target - refIdx)
        ti >= tracks.size || (from.segment(id) match {
          case c: Content => tracks(ti).canPlaceAt(c, from.interval(id), moving)
          case _ => false
        })
      }
    }
  }

  /** 在 [time±threshold] 内扫描所有轨道条目，返回最近的起点/终点。 */
  def snapTime(time: Instant, threshold: Timespan, ignore: scala.collection.Set[Id]): Instant = {
    var best = time
    var bestDist = threshold
    val searchStart: Instant = Instant.Zero.max(time - threshold)
    val searchEnd: Instant = time + threshold
    if (searchEnd <= searchStart) return time
    val searchInterval: Interval = searchStart ~~ searchEnd
    for (track <- tracks) {
      for (s <- track.getIntersecting(searchInterval)) {
        if (!ignore.contains(s.id)) {
          val r = track.interval(s.id)
          var dist = (r.lo - time).abs
          if (dist < bestDist) {
            best = r.lo
            bestDist = dist
          }
          dist = (r.hi - time).abs
          if (dist < bestDist) {
            best = r.hi
            bestDist = dist
          }
        }
      }
    }
    val zeroDist = time - Instant.Zero
    if (zeroDist < threshold && zeroDist < bestDist) {
      best = Instant.Zero
    }
    best
  }

  /** 持有该片段的轨道。 */
  def findTrackOf(id: Id): Option[Track] =
    tracks.find(_.contains(id))

  /** 按 id 解析片段。 */
  def segment(id: Id): Option[Segment] = {
    if (id == Id.noId()) None
    else tracks.iterator.flatMap(_.segmentOption(id)).nextOption()
  }

  def newGroup(): (Timeline, SegmentGroup) = {
    val group = SegmentGroup(Set.empty)
    (Timeline(tracks, groups :+ group), group)
  }

  def adoptGroup(group: SegmentGroup): Timeline = {
    if (groups.contains(group)) this else Timeline(tracks, groups :+ group)
  }

  def dropGroup(group: SegmentGroup): Timeline = {
    Timeline(tracks, groups.filterNot(_ == group))
  }

  def replaceGroup(old: SegmentGroup, next: SegmentGroup): Timeline = {
    val i = groups.indexOf(old)
    if (i < 0) this else Timeline(tracks, groups.updated(i, next))
  }

  def getGroup(id: Id): Option[SegmentGroup] = {
    groups.find(_.contains(id))
  }

  def getTrackOrCreate(index: Int): Track = {
    val ts = tracks
    if (index < ts.size) ts(index) else Track(index)
  }

  def getTrackCount: Int = tracks.size

  override def toString: String = {
    val body = tracks.zipWithIndex
      .map((track, i) => System.lineSeparator() + "track#" + i + ":" + track)
      .mkString
    "Timeline:" + body
  }
  def getLength: Instant = {
    if (tracks.isEmpty) Instant.Zero else tracks.iterator.map(_.length).max
  }
  def getTracks: Seq[Track] = {
    tracks
  }

  lazy val maxId: Id =
    tracks.iterator.flatMap(_.iterator).maxOption.getOrElse(Id.noId())

  /** 迭代有元素的轨道。 */
  override def iterator: Iterator[Track] = tracks.iterator.filterNot(_.isEmpty)

}

object Timeline {
  def empty: Timeline = Timeline(Vector.empty[Track], Vector.empty[SegmentGroup])
}
