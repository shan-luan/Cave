package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.{Id, IdSource}
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
  def add(trackIndex: Int, segment: Content, interval: Interval, origin: Long)(using ids: IdSource): Timeline = {
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

  def split(track: Track, time: Long): Timeline = {
    val current = getTrackOrCreate(track.index)
    if (current.canSplit(time)) withTrack(track.index, current.split(time)) else this
  }

  def setStart(ids: Iterable[Id], deltaTime: Long)(using source: IdSource): (Timeline, Long) = {
    applyPerTrack(ids, deltaTime, false)
  }

  def setEnd(ids: Iterable[Id], deltaTime: Long)(using source: IdSource): (Timeline, Long) = {
    applyPerTrack(ids, deltaTime, true)
  }

  private def applyPerTrack(ids: Iterable[Id], deltaTime: Long, end: Boolean)(using source: IdSource): (Timeline, Long) = {
    if (deltaTime == 0 || ids.isEmpty) return (this, 0L)
    val forward = deltaTime > 0
    val indices = trackIndicesOf(ids)
    if (indices.isEmpty) return (this, 0L)

    val bounds = indices
      .map((i: Int) => { val t = tracks(i); if (end) t.endLimit(ids, forward) else t.startLimit(ids, forward) })
    val bound: Long = if (forward) bounds.min else bounds.max
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return (this, 0L)

    val edits = mutable.ArrayBuffer.empty[(Int, Track)]
    for (i <- indices) {
      val before = tracks(i)
      val after = if (end) before.setEnd(ids, applied) else before.setStart(ids, applied)
      edits += (i -> after)
    }
    (withTracks(edits.toSeq), applied)
  }

  def moveTime(ids: Iterable[Id], deltaTime: Long)(using source: IdSource): (Timeline, Long) = {
    if (deltaTime == 0 || ids.isEmpty) return (this, 0L)
    val forward = deltaTime > 0
    val indices = trackIndicesOf(ids)
    if (indices.isEmpty) return (this, 0L)

    val bounds = indices.map((i: Int) => tracks(i).moveLimit(ids, forward))
    val bound: Long = if (forward) bounds.min else bounds.max
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return (this, 0L)

    val idsVector = ids.toVector
    val idSet = idsVector.toSet
    val edits = mutable.ArrayBuffer.empty[(Int, Track)]
    for (i <- indices) {
      val before = tracks(i)
      // 内容先全部摘掉，再按新位置放回。转场是重叠区的派生物，随内容自动重建；
      // 只有两侧内容都不在本次搬运范围内时，才需要把转场本身当作搬运动作
      val onTrack = idsVector.filter(id => before.contains(id)).flatMap(id => before.segmentOption(id))
      var next = before
      for (s <- onTrack) {
        s match {
          case c: Content => next = next.remove(c.id)
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
   * 仅按轨道索引平移片段（时间区间不变）。deltaTrack 截断到最大可用量后应用，保持组内成员相对间距。
   * @return 实际应用的轨道偏移；0 表示该方向无法移动，保持原位。
   */
  def moveTrack(ids: Iterable[Id], deltaTrack: Int)(using source: IdSource): (Timeline, Int) = {
    val idsVector = ids.toVector
    // 转场不允许换轨
    if (idsVector.exists(id => segment(id).exists(_.isInstanceOf[Transition]))) {
      return (this, 0)
    }
    val applied = findPlaceableTrack(idsVector, deltaTrack)
    if (applied == 0) return (this, 0)

    val moves = mutable.ArrayBuffer.empty[(Content, Int, Int, Interval, Long)]
    for (id <- idsVector) {
      findTrackOf(id).foreach { from =>
        // 入口已排除转场
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
    // 先全部摘除再全部放回。片段可能在成员之间换轨，摘除不完全会让放置被自己挡住
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
    (new Timeline(out, groups), applied)
  }

  /** 涉及到的轨道索引，按出现顺序去重。 */
  private def trackIndicesOf(ids: Iterable[Id]): mutable.LinkedHashSet[Int] = {
    val indices = mutable.LinkedHashSet.empty[Int]
    for (id <- ids) {
      findTrackOf(id).foreach(t => indices.add(t.index))
    }
    indices
  }

  /** 成员所在的轨道；要求成员在时间线上，否则抛 IllegalArgumentException。 */
  private def requiredTrack(id: Id): Track =
    findTrackOf(id).getOrElse(throw new IllegalArgumentException(("片段不在时间线上: ".in(Zh) | "Segment is not on the timeline: ".in(En)).str() + id))

  /** 在 deltaTrack 方向上找出整组可放置的最大轨道偏移（带符号，绝对值 ≤ |deltaTrack|）。 */
  private def findPlaceableTrack(ids: Iterable[Id], deltaTrack: Int): Int = {
    if (deltaTrack == 0 || ids.isEmpty) return 0
    val minIdx: Int = ids.iterator.map((id: Id) => requiredTrack(id).index).min
    val step = if (deltaTrack > 0) 1 else -1
    val span = Math.abs(deltaTrack)
    var k = span
    while (k > 0) {
      // 目标轨道尚不存在（索引 ≥ tracks.size）时视为空闲
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
        // 目标轨道尚不存在（索引 ≥ tracks.size）时视为空闲。
        // 落点与既有内容重叠只要合法就允许，重叠会成为转场，不必强行换到空轨道
        ti >= tracks.size || (from.segment(id) match {
          case c: Content => tracks(ti).canPlaceAt(c, from.interval(id), moving)
          case _ => false
        })
      }
    }
  }

  /** 在 [time±threshold] 内扫描所有轨道条目，返回最近的起点/终点（无则原值）；ignore 不参与。 */
  def snapTime(time: Long, threshold: Long, ignore: scala.collection.Set[Id]): Long = {
    var best = time
    var bestDist = threshold
    val searchStart: Long = Math.max(0, time - threshold)
    val searchEnd: Long = time + threshold
    if (searchEnd <= searchStart) return time
    val searchInterval: Interval = searchStart ~~ searchEnd
    for (track <- tracks) {
      for (s <- track.getIntersecting(searchInterval)) {
        if (!ignore.contains(s.id)) {
          val r = track.interval(s.id)
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
  def findTrackOf(id: Id): Option[Track] =
    tracks.find(_.contains(id))

  /** 按 id 解析片段；block 与未放置的片段返回 None。 */
  def segment(id: Id): Option[Segment] = {
    if (id == Id.noId()) None
    else tracks.iterator.flatMap(_.segmentOption(id)).nextOption()
  }

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

  def getGroup(id: Id): Option[SegmentGroup] = {
    groups.find(_.contains(id))
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

  lazy val maxId: Id =
    tracks.iterator.flatMap(_.iterator).maxOption.getOrElse(Id.noId())

  /** 迭代有元素的轨道。 */
  override def iterator: Iterator[Track] = tracks.iterator.filterNot(_.isEmpty)

}

object Timeline {
  def empty: Timeline = new Timeline(Vector.empty[Track], Vector.empty[SegmentGroup])
}
