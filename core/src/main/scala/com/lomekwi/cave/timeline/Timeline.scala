package com.lomekwi.cave.timeline

import com.lomekwi.cave.app.selection.SourceNodeChangedEvent
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.timeline.UndoManager.{AddSegCommand, CompoundCommand, MergeableCommand, MoveSegsCommand, RemoveSegCommand, RemoveSegsCommand, ResizeSegsCommand, SplitSegCommand, UndoableCommand}
import com.lomekwi.cave.util.Duplicatable

import java.io.{ObjectInputStream, Serializable}
import java.util

import scala.jdk.CollectionConverters.*

@SerialVersionUID(1L)
class Timeline(final val project: Project) extends Serializable with java.lang.Iterable[Track] with Duplicatable[Timeline] {
  @transient private var recording: Boolean = false
  @transient private var recorded: util.List[UndoableCommand] = new util.ArrayList[UndoableCommand]()
  private final val tracks: util.List[Track] = new util.ArrayList[Track]()
  /** 分组注册表。组是跨轨道的，故不归属任何单条轨道。 */
  private final val groups: util.List[SourceGroup] = new util.ArrayList[SourceGroup]()

  private def readObject(in: ObjectInputStream): Unit = {
    in.defaultReadObject()
    for (track <- tracks.asScala) {
      track.setTimeline(this)
    }
    recording = false
    recorded = new util.ArrayList[UndoableCommand]()
  }

  def tryAdd(track: Track, source: Source[?], range: Interval, origin: Long): Long = {
    val shift = track.tryAdd(source, range, origin)
    if (shift == 0) {
      push(AddSegCommand(track, source, range, origin))
    }
    shift
  }
  protected[timeline] def addOrThrow(track: Track, source: Source[?], range: Interval, origin: Long): Unit = {
    track.addOrThrow(source, range, origin)
    push(AddSegCommand(track, source, range, origin))
  }
  def remove(source: Source[?]): Unit = {
    val track = findTrackOf(source)
    if (track != null) {
      val range = track.getRange(source)
      val origin = track.getOrigin(source)
      val group = getGroup(source)
      if (track.remove(source)) {
        push(RemoveSegCommand(track, source, range, origin, group))
      }
    }
  }
  def remove(sources: util.Collection[Source[?]]): Unit = {
    val entries: util.List[RemoveSegsCommand.RemoveEntry] = new util.ArrayList[RemoveSegsCommand.RemoveEntry](sources.size())
    for (s <- sources.asScala) {
      val track = findTrackOf(s)
      if (track != null) {
        val range = track.getRange(s)
        val origin = track.getOrigin(s)
        val group = getGroup(s)
        if (track.remove(s)) {
          entries.add(RemoveSegsCommand.RemoveEntry(track, s, range, origin, group))
        }
      }
    }
    if (!entries.isEmpty) {
      push(new RemoveSegsCommand(entries))
    }
  }

  def split(track: Track, time: Long): Unit = {
    track.get(time) match {
      case Segment(s) =>
        val r = track.getRange(s)
        val lo: Long = r.lo
        val hi: Long = r.hi
        if (time > lo && time < hi && track.split(time)) {
          val right = track.get(time) match {
            case Segment(rightSeg) => rightSeg
            case _: Gap | null => null
          }
          push(SplitSegCommand(track, s, r, track.getOrigin(s), right, track.getOrigin(right), time))
        }
      case _: Gap | null =>
    }
  }

  /** 裁切一组源的起始边缘（各自终点不变）。@return 实际应用的偏移量（截断到最大可用量），0 表示未移动。 */
  def setStart(sources: util.Collection[Source[?]], deltaTime: Long): Long = {
    applyPerTrack(sources, deltaTime, false)
  }

  /** 裁切一组源的结束边缘（各自起点不变）。@return 同 {@link #setStart}。 */
  def setEnd(sources: util.Collection[Source[?]], deltaTime: Long): Long = {
    applyPerTrack(sources, deltaTime, true)
  }

  /** 各轨道 probe 后取限制最严者，把 deltaTime 同向截断并应用。@return 实际应用的偏移量，0 表示未移动。 */
  private def applyPerTrack(sources: util.Collection[Source[?]], deltaTime: Long, end: Boolean): Long = {
    if (deltaTime == 0 || sources.isEmpty) return 0L
    val forward = deltaTime > 0
    val tracksOf: util.Set[Track] = new util.HashSet[Track]()
    for (s <- sources.asScala) {
      val t = findTrackOf(s)
      if (t != null) tracksOf.add(t)
    }
    if (tracksOf.isEmpty) return 0L

    val bound: Long = tracksOf.stream()
      .mapToLong((track: Track) => if (end) track.probeSetEnd(sources, forward)
                                    else track.probeSetStart(sources, forward))
      .reduce(if (forward) Long.MaxValue else Long.MinValue, Track.tighter)
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return 0L

    val before: util.Map[Source[?], Interval] = new util.HashMap[Source[?], Interval]()
    for (s <- sources.asScala) {
      val track = findTrackOf(s)
      if (track != null) before.put(s, track.getRange(s))
    }
    for (track <- tracksOf.asScala) {
      if (end) track.setEnd(sources, applied)
      else track.setStart(sources, applied)
    }
    val entries: util.List[ResizeSegsCommand.ResizeEntry] = new util.ArrayList[ResizeSegsCommand.ResizeEntry]()
    for (s <- sources.asScala) {
      val track = findTrackOf(s)
      val old = before.get(s)
      if (track != null && old != null) {
        val updated = track.getRange(s)
        if (!old.equals(updated)) {
          entries.add(ResizeSegsCommand.ResizeEntry(track, s, track.getOrigin(s), old, updated))
        }
      }
    }
    if (!entries.isEmpty) {
      push(new ResizeSegsCommand(entries))
    }
    applied
  }

  /** 仅按时间平移源（轨道不变）。deltaTime 截断到最大可用量后应用：整组最多移到与障碍贴合。@return 实际应用的偏移量；0 表示未移动。 */
  def moveTime(sources: util.Collection[Source[?]], deltaTime: Long): Long = {
    if (deltaTime == 0 || sources.isEmpty) return 0L
    val forward = deltaTime > 0
    val tracksOf: util.Set[Track] = new util.HashSet[Track]()
    for (s <- sources.asScala) {
      val t = findTrackOf(s)
      if (t != null) tracksOf.add(t)
    }
    if (tracksOf.isEmpty) return 0L

    val bound: Long = tracksOf.stream()
      .mapToLong((tr: Track) => tr.probeMove(sources, forward))
      .reduce(if (forward) Long.MaxValue else Long.MinValue, Track.tighter)
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return 0L

    // 先构造命令再移动（移除直接走 Track，避免重复记录）
    val entries: util.List[MoveSegsCommand.MoveEntry] = new util.ArrayList[MoveSegsCommand.MoveEntry](sources.size())
    for (s <- sources.asScala) {
      val track = findTrackOf(s)
      val r = track.getRange(s)
      val origin = track.getOrigin(s)
      entries.add(MoveSegsCommand.MoveEntry(track, track, s, r, r.shift(applied), origin, origin + applied))
    }
    for (s <- sources.asScala) {
      val t = findTrackOf(s)
      if (t != null) t.remove(s)
    }
    for (e <- entries.asScala) {
      e.toTrack.addOrThrow(e.source, e.newRange, e.newOrigin)
    }
    push(new MoveSegsCommand(entries))
    applied
  }

  /**
   * 仅按轨道索引平移源（时间区间不变）。deltaTrack 会被同向截断到最大可用的
   * 轨道偏移后应用：从请求的目标轨道起沿该方向逐条回退，落在第一条整组可放置的
   * 轨道上（不反向、不超过请求量），保持组内成员相对间距。
   * @return 实际应用的轨道偏移；0 表示该方向无法移动，保持原位。
   */
  def moveTrack(sources: util.Collection[Source[?]], deltaTrack: Int): Int = {
    val applied = findPlaceableTrack(sources, deltaTrack)
    if (applied == 0) return 0

    val entries: util.List[MoveSegsCommand.MoveEntry] = new util.ArrayList[MoveSegsCommand.MoveEntry](sources.size())
    for (s <- sources.asScala) {
      val from = findTrackOf(s)
      val to = getTrack(from.index + applied)
      if (!from.eq(to)) {
        val r = from.getRange(s)
        val origin = from.getOrigin(s)
        entries.add(MoveSegsCommand.MoveEntry(from, to, s, r, r, origin, origin))
      }
    }
    for (s <- sources.asScala) {
      val t = findTrackOf(s)
      if (t != null) t.remove(s)
    }
    for (e <- entries.asScala) {
      e.toTrack.addOrThrow(e.source, e.newRange, e.newOrigin)
    }
    if (!entries.isEmpty) {
      push(new MoveSegsCommand(entries))
    }
    applied
  }

  /**
   * 在 deltaTrack 方向上找出整组可放置的最大轨道偏移（带符号，绝对值 ≤ |deltaTrack|）：
   * 从请求量开始向 0 逐级回退探测，返回第一条可放置轨道对应的偏移。
   * 索引越大的轨道越可能为空，且 getTrack 会按需创建，因此正向探测总能找到落点；
   * 反向受 0 限制，找不到时返回 0（保持原位）。
   */
  private def findPlaceableTrack(sources: util.Collection[Source[?]], deltaTrack: Int): Int = {
    if (deltaTrack == 0 || sources.isEmpty) return 0
    val minIdx = sources.stream().mapToInt((s: Source[?]) => findTrackOf(s).index).min().orElseThrow()
    val step = if (deltaTrack > 0) 1 else -1
    val span = Math.abs(deltaTrack)
    var k = span
    while (k > 0) {
      // 目标轨道尚不存在（索引 ≥ tracks.size()）时视为空闲
      val target = minIdx + deltaTrack - step * (span - k)
      if (canPlaceGroupOnTrack(sources, target)) return step * k
      k -= 1
    }
    0
  }

  /**
   * 整组按统一偏移移动后，是否每个成员在各自目标轨道上都不与既有源冲突。
   * 目标轨道尚不存在（索引 ≥ tracks.size()）时视为空闲。
   */
  private def canPlaceGroupOnTrack(sources: util.Collection[Source[?]], target: Int): Boolean = {
    if (target < 0) {
      false
    } else {
      var refIdx = findTrackOf(sources.iterator().next()).index
      for (s <- sources.asScala) refIdx = Math.min(refIdx, findTrackOf(s).index)
      sources.asScala.forall { s =>
        val ti = findTrackOf(s).index + (target - refIdx)
        ti >= tracks.size() || tracks.get(ti).isFree(findTrackOf(s).getRange(s), sources)
      }
    }
  }

  /** 在 [time±threshold] 内扫描所有轨道条目，返回最近的起点/终点（无则原值）；ignore 不参与。 */
  def snapTime(time: Long, threshold: Long, ignore: util.Collection[Source[?]]): Long = {
    var best = time
    var bestDist = threshold
    val searchStart: Long = Math.max(0, time - threshold)
    val searchEnd: Long = time + threshold
    if (searchEnd <= searchStart) return time
    val searchRange: Interval = Interval(searchStart, searchEnd)
    for (track <- tracks.asScala) {
      for (element <- track.getIntersecting(searchRange).asScala) {
        element match {
          case Segment(source) =>
            if (!ignore.contains(source)) {
              val r = track.getRange(source)
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
          case _: Gap =>
        }
      }
    }
    if (time < threshold && time < bestDist) {
      best = 0
    }
    best
  }

  /** 持有该源的轨道；未放置时返回 null。 */
  def findTrackOf(source: Source[?]): Track = {
    for (track <- tracks.asScala) {
      if (track.contains(source)) return track
    }
    null
  }

  /**
   * 新建一个组并纳入注册表。组跨轨道，因此注册表在时间线一级。
   */
  def newGroup(): SourceGroup = {
    val group = new SourceGroup()
    groups.add(group)
    group
  }

  /** 把外部构造的组（如剪贴板模板）纳入注册表。 */
  def adoptGroup(group: SourceGroup): SourceGroup = {
    if (!groups.contains(group)) {
      groups.add(group)
    }
    group
  }

  /** 把组移出注册表，此后 {@link #getGroup} 不再能查到它。 */
  def dropGroup(group: SourceGroup): Unit = {
    groups.remove(group)
  }

  /** 源所属的组；不属于任何组时返回 null。 */
  def getGroup(source: Source[?]): SourceGroup = {
    groups.asScala.find(_.contains(source)).orNull
  }

  /**
   * 开始记录：此后到 {@link #submit()} 之间对时间轴的每次修改都会记录一条命令，
   * 最终在 close/submit 时合并为一条命令提交。
   */
  def record(): Timeline.Recording = {
    assert(!recording)
    recording = true
    recorded.clear()
    () => submit()
  }
  /**
   * 结束记录，并把期间记录的所有命令合并为一条命令提交到项目的命令栈。
   * 若期间没有修改则不提交。
   */
  def submit(): Unit = {
    if (recording) {
      recording = false
      if (!recorded.isEmpty) {
        if (recorded.size() == 1) {
          project.undoManager.record(recorded.get(0))
        } else {
          val arr = recorded.toArray(new Array[UndoableCommand](0))
          project.undoManager.record(new CompoundCommand(arr*))
        }
        recorded.clear()
      }
    }
  }
  /** 记录模式下把一次修改对应的命令压入记录栈。同类型命令会与栈尾合并。 */
  private def push(command: UndoableCommand): Unit = {
    if (recording) {
      val last = if (recorded.isEmpty) null else recorded.get(recorded.size() - 1)
      val mergeable = last != null && command.isInstanceOf[MergeableCommand] && last.getClass == command.getClass
      if (!mergeable || !last.asInstanceOf[MergeableCommand].merge(command)) {
        recorded.add(command)
      }
    }
  }

  /** 获取指定索引的轨道，不存在则自动创建。 */
  def getTrack(index: Int): Track = {
    while (tracks.size() <= index) {
      tracks.add(new Track(this, tracks.size()))
    }
    tracks.get(index)
  }

  override def toString: String = {
    val body = tracks.asScala.zipWithIndex
      .map((track, i) => System.lineSeparator() + "track#" + i + ":" + track)
      .mkString
    "Timeline:" + body
  }
  def getLength: Long = {
    tracks.stream()
      .mapToLong((t: Track) => t.getLength)
      .max()
      .orElse(0)
  }
  def getTracks: util.List[Track] = {
    tracks
  }

  /**
   * 两个时间线相等，当且仅当每个轨道对应相等：按索引逐位比较轨道内容。
   * 由于 {@link #getTrack(int)} 会按需自动创建空轨道、而撤销不会删除轨道，
   * 比较时把"缺失"与"空轨道"视为相等（只允许尾部为空的差异）。
   */
  override def equals(o: Any): Boolean = {
    if (this.asInstanceOf[AnyRef] eq o.asInstanceOf[AnyRef]) {
      true
    } else o match {
      case other: Timeline =>
        val max = Math.max(tracks.size(), other.tracks.size())
        (0 until max).forall { i =>
          val a = if (i < tracks.size()) tracks.get(i) else null
          val b = if (i < other.tracks.size()) other.tracks.get(i) else null
          Timeline.trackEquals(a, b)
        }
      case _ => false
    }
  }

  override def hashCode(): Int = {
    var h = 1
    for (track <- tracks.asScala) {
      if (!track.isEmpty) {
        h = 31 * h + track.hashCode()
      }
    }
    h
  }

  /**
   * 迭代有元素的轨道
   * @return 轨道迭代器
   */
  override def iterator(): util.Iterator[Track] = {
    new IteratorImpl()
  }

  class IteratorImpl extends util.Iterator[Track] {
    private var index: Int = 0

    private def skipEmpty(): Unit = {
      while (index < tracks.size() && tracks.get(index).isEmpty) {
        index += 1
      }
    }

    override def hasNext: Boolean = {
      skipEmpty()
      index < tracks.size()
    }

    override def next(): Track = {
      if (!hasNext) throw new util.NoSuchElementException()
      val t = tracks.get(index)
      index += 1
      t
    }
  }
}

object Timeline {
  /** 记录句柄，用于 try-with-resources：close() 即 {@link #submit()}。 */
  trait Recording extends AutoCloseable {
    override def close(): Unit
  }

  private def trackEquals(a: Track, b: Track): Boolean = {
    if (a == null) b == null || b.isEmpty
    else if (b == null) a.isEmpty
    else a.equals(b)
  }
}
