package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.{Content, Segment, Transition}
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.timeline.UndoManager.{AddSegmentCommand, CompoundCommand, MergeableCommand, MoveSegmentsCommand, RemoveSegmentCommand, RemoveSegmentsCommand, ResizeSegmentsCommand, SplitSegmentCommand, TrackEdit, UndoableCommand}
import com.lomekwi.cave.playback.TrackWorker
import com.lomekwi.cave.util.Duplicatable

import java.io.{ObjectInputStream, Serializable}
import java.util

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/**
 * 时间线。它是这套结构里唯一可变的地方，编辑产生新版本，所有读取方（界面、播放线程、导出）
 * 都从这里取最新版本。跨轨道的批量操作一次性发布，对读者是原子的。
 */
@SerialVersionUID(1L)
class Timeline(final val project: Project) extends Serializable with java.lang.Iterable[Track] with Duplicatable[Timeline] {
  @transient private var recording: Boolean = false
  @transient private var recorded: util.List[UndoableCommand] = new util.ArrayList[UndoableCommand]()
  @volatile private var tracks: Vector[Track] = Vector.empty[Track]
  @transient private var workers: util.Map[Integer, TrackWorker] = new util.HashMap[Integer, TrackWorker]()
  private final val groups: util.List[SegmentGroup] = new util.ArrayList[SegmentGroup]()

  private def readObject(in: ObjectInputStream): Unit = {
    in.defaultReadObject()
    recording = false
    recorded = new util.ArrayList[UndoableCommand]()
    workers = new util.HashMap[Integer, TrackWorker]()
  }

  /** 把 index 处的轨道替换为新版本。 */
  protected[timeline] def setTrack(index: Int, track: Track): Unit = publish(Seq(index -> track))

  /** 一次性替换多条轨道。 */
  protected[timeline] def setTracks(changes: Seq[(Int, Track)]): Unit = publish(changes)

  private def publish(changes: Seq[(Int, Track)]): Unit = {
    if (changes.isEmpty) return
    var ts = tracks
    for ((i, t) <- changes) {
      while (ts.size <= i) {
        ts = ts :+ Track(this, ts.size)
      }
      ts = ts.updated(i, t)
    }
    tracks = ts
    for ((i, _) <- changes) {
      getWorker(i).onTrackChanged()
    }
  }

  def tryAdd(track: Track, segment: Content, range: Interval, origin: Long): Long = {
    val current = getTrackOrCreate(track.index)
    val (next, shift) = current.tryAdd(segment, range, origin)
    if (shift == 0) {
      setTrack(track.index, next)
      push(AddSegmentCommand(this, track.index, current, next))
    }
    shift
  }

  protected[timeline] def addOrThrow(track: Track, segment: Content, range: Interval, origin: Long): Unit = {
    val current = getTrackOrCreate(track.index)
    val next = current.addOrThrow(segment, range, origin)
    setTrack(track.index, next)
    push(AddSegmentCommand(this, track.index, current, next))
  }

  def remove(segment: Segment): Unit = {
    val track = findTrackOf(segment)
    if (track != null) {
      val group = getGroup(segment)
      val next = track.remove(segment)
      setTrack(track.index, next)
      if (group != null) group.remove(segment)
      push(RemoveSegmentCommand(this, track.index, track, next, segment, group))
    }
  }

  def remove(segments: util.Collection[Segment]): Unit = {
    val working = mutable.HashMap.empty[Int, Track]
    def currentOf(i: Int): Track = working.getOrElse(i, tracks(i))
    val entries = new util.ArrayList[RemoveSegmentsCommand.RemoveEntry](segments.size())
    for (s <- segments.asScala) {
      val track = findTrackOf(s)
      if (track != null) {
        val group = getGroup(s)
        val before = currentOf(track.index)
        val next = before.remove(s)
        working.put(track.index, next)
        if (group != null) group.remove(s)
        entries.add(RemoveSegmentsCommand.RemoveEntry(TrackEdit(track.index, before, next), s, group))
      }
    }
    if (!entries.isEmpty) {
      setTracks(working.toSeq)
      push(new RemoveSegmentsCommand(this, entries))
    }
  }

  def split(track: Track, time: Long): Unit = {
    val current = getTrackOrCreate(track.index)
    if (current.canSplit(time)) {
      val next = current.split(time)
      setTrack(track.index, next)
      push(SplitSegmentCommand(this, track.index, current, next))
    }
  }

  /** 裁切一组片段的起始边缘（各自终点不变）。返回实际应用的偏移量（截断到最大可用量），0 表示未移动。 */
  def setStart(segments: util.Collection[Segment], deltaTime: Long): Long = {
    applyPerTrack(segments, deltaTime, false)
  }

  /** 裁切一组片段的结束边缘（各自起点不变）。返回值语义同 [[Timeline.setStart]]。 */
  def setEnd(segments: util.Collection[Segment], deltaTime: Long): Long = {
    applyPerTrack(segments, deltaTime, true)
  }

  private def applyPerTrack(segments: util.Collection[Segment], deltaTime: Long, end: Boolean): Long = {
    if (deltaTime == 0 || segments.isEmpty) return 0L
    val forward = deltaTime > 0
    val indices = trackIndicesOf(segments)
    if (indices.isEmpty) return 0L

    val bounds = indices
      .map((i: Int) => { val t = tracks(i); if (end) t.probeSetEnd(segments, forward) else t.probeSetStart(segments, forward) })
    val bound: Long = if (forward) bounds.min else bounds.max
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return 0L

    val edits = new util.ArrayList[TrackEdit]()
    for (i <- indices) {
      val before = tracks(i)
      val after = if (end) before.setEnd(segments, applied) else before.setStart(segments, applied)
      edits.add(TrackEdit(i, before, after))
    }
    setTracks(edits.asScala.map(e => e.index -> e.after).toSeq)
    push(new ResizeSegmentsCommand(this, edits))
    applied
  }

  /** 仅按时间平移片段（轨道不变）。`deltaTime` 截断到最大可用量后应用，整组最多移到与障碍贴合。返回实际应用的偏移量，0 表示未移动。 */
  def moveTime(segments: util.Collection[Segment], deltaTime: Long): Long = {
    if (deltaTime == 0 || segments.isEmpty) return 0L
    val forward = deltaTime > 0
    val indices = trackIndicesOf(segments)
    if (indices.isEmpty) return 0L

    val bounds = indices.map((i: Int) => tracks(i).probeMove(segments, forward))
    val bound: Long = if (forward) bounds.min else bounds.max
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return 0L

    val edits = new util.ArrayList[TrackEdit]()
    for (i <- indices) {
      val before = tracks(i)
      // 内容先全部摘掉，再按新位置放回。转场是重叠区的派生物，随内容自动重建；
      // 只有两侧内容都不在本次搬运范围内时，才需要把转场本身当作搬运动作
      val onTrack = new util.ArrayList[Segment]()
      for (s <- segments.asScala) {
        if (before.contains(s)) onTrack.add(s)
      }
      var next = before
      for (s <- onTrack.asScala) {
        s match {
          case c: Content => next = next.remove(c)
          case _: Transition =>
        }
      }
      // 内容自右向左放回：放每个内容时它的右邻居已经就位，右侧原有的转场对象才接得上。
      // 从左往右的话右邻居还在缺席，转场会被当成不再重叠而摘掉，放回后只能新建一个
      val refill = new util.ArrayList[Content](onTrack.size())
      for (s <- onTrack.asScala) {
        s match {
          case c: Content => refill.add(c)
          case _: Transition =>
        }
      }
      refill.sort(java.util.Comparator.comparingLong((c: Content) => before.getRange(c).lo).reversed())
      for (c <- refill.asScala) {
        next = next.addOrThrow(c, before.getRange(c).shift(applied), before.getOrigin(c) + applied)
      }
      for (s <- onTrack.asScala) {
        s match {
          case t: Transition =>
            val sides = before.transitionSides(t)
            if (sides != null && !segments.contains(sides._1) && !segments.contains(sides._2)) {
              next = next.shiftTransition(t, applied)
            }
          case _ =>
        }
      }
      edits.add(TrackEdit(i, before, next))
    }
    setTracks(edits.asScala.map(e => e.index -> e.after).toSeq)
    push(new MoveSegmentsCommand(this, edits))
    applied
  }

  /**
   * 仅按轨道索引平移片段（时间区间不变）。deltaTrack 截断到最大可用量后应用，保持组内成员相对间距。
   * @return 实际应用的轨道偏移；0 表示该方向无法移动，保持原位。
   */
  def moveTrack(segments: util.Collection[Segment], deltaTrack: Int): Int = {
    // 转场不允许换轨
    if (segments.asScala.exists {
      case _: Transition => true
      case _ => false
    }) {
      return 0
    }
    val applied = findPlaceableTrack(segments, deltaTrack)
    if (applied == 0) return 0

    val moves = new util.ArrayList[(Content, Int, Int, Interval, Long)]()
    for (s <- segments.asScala) {
      val from = findTrackOf(s)
      if (from != null) {
        // 入口已排除转场
        val c = s.asInstanceOf[Content]
        val to = getTrackOrCreate(from.index + applied)
        moves.add((c, from.index, to.index, from.getRange(c), from.getOrigin(c)))
      }
    }
    if (moves.isEmpty) return 0

    val working = mutable.HashMap.empty[Int, Track]
    def currentOf(i: Int): Track = working.getOrElse(i, tracks(i))
    val beforeOf = mutable.HashMap.empty[Int, Track]
    for ((_, fromIndex, toIndex, _, _) <- moves.asScala) {
      beforeOf.getOrElseUpdate(fromIndex, tracks(fromIndex))
      beforeOf.getOrElseUpdate(toIndex, tracks(toIndex))
    }
    // 先全部摘除再全部放回。片段可能在成员之间换轨，摘除不完全会让放置被自己挡住
    for ((s, fromIndex, _, _, _) <- moves.asScala) {
      working.put(fromIndex, currentOf(fromIndex).remove(s))
    }
    for ((s, _, toIndex, range, origin) <- moves.asScala) {
      val before = currentOf(toIndex)
      working.put(toIndex, before.addOrThrow(s, range, origin))
    }

    val edits = new util.ArrayList[TrackEdit]()
    for ((i, before) <- beforeOf) {
      edits.add(TrackEdit(i, before, currentOf(i)))
    }
    setTracks(edits.asScala.map(e => e.index -> e.after).toSeq)
    push(new MoveSegmentsCommand(this, edits))
    applied
  }

  /** 涉及到的轨道索引，按出现顺序去重。 */
  private def trackIndicesOf(segments: util.Collection[Segment]): mutable.LinkedHashSet[Int] = {
    val indices = mutable.LinkedHashSet.empty[Int]
    for (s <- segments.asScala) {
      val t = findTrackOf(s)
      if (t != null) indices.add(t.index)
    }
    indices
  }

  /** 在 deltaTrack 方向上找出整组可放置的最大轨道偏移（带符号，绝对值 ≤ |deltaTrack|）。 */
  private def findPlaceableTrack(segments: util.Collection[Segment], deltaTrack: Int): Int = {
    if (deltaTrack == 0 || segments.isEmpty) return 0
    val minIdx: Int = segments.asScala.iterator.map((s: Segment) => findTrackOf(s).index).min
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
  private def canPlaceGroupOnTrack(segments: util.Collection[Segment], target: Int): Boolean = {
    if (target < 0) {
      false
    } else {
      var refIdx = findTrackOf(segments.iterator().next()).index
      for (s <- segments.asScala) refIdx = Math.min(refIdx, findTrackOf(s).index)
      segments.asScala.forall { s =>
        val from = findTrackOf(s)
        val ti = from.index + (target - refIdx)
        // 目标轨道尚不存在（索引 ≥ tracks.size）时视为空闲。
        // 落点与既有内容重叠只要合法就允许，重叠会成为转场，不必强行换到空轨道
        ti >= tracks.size || (s match {
          case c: Content => tracks(ti).canPlaceAt(c, from.getRange(s), segments)
          case _ => false
        })
      }
    }
  }

  /** 在 [time±threshold] 内扫描所有轨道条目，返回最近的起点/终点（无则原值）；ignore 不参与。 */
  def snapTime(time: Long, threshold: Long, ignore: util.Collection[Segment]): Long = {
    var best = time
    var bestDist = threshold
    val searchStart: Long = Math.max(0, time - threshold)
    val searchEnd: Long = time + threshold
    if (searchEnd <= searchStart) return time
    val searchRange: Interval = searchStart ~~ searchEnd
    for (track <- tracks) {
      for (s <- track.getIntersecting(searchRange).asScala) {
        if (!ignore.contains(s)) {
          val r = track.getRange(s)
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

  /** 持有该片段的轨道；未放置时返回 null。 */
  def findTrackOf(segment: Segment): Track =
    tracks.find(_.contains(segment)).orNull

  /** 新建一个组并纳入注册表。 */
  def newGroup(): SegmentGroup = {
    val group = new SegmentGroup()
    groups.add(group)
    group
  }

  /** 把外部构造的组（如剪贴板模板）纳入注册表。 */
  def adoptGroup(group: SegmentGroup): SegmentGroup = {
    if (!groups.contains(group)) {
      groups.add(group)
    }
    group
  }

  /** 把组移出注册表，此后 [[Timeline.getGroup]] 不再能查到它。 */
  def dropGroup(group: SegmentGroup): Unit = {
    groups.remove(group)
  }

  /** 片段所属的组；不属于任何组时返回 null。 */
  def getGroup(segment: Segment): SegmentGroup = {
    groups.asScala.find(_.contains(segment)).orNull
  }

  /**
   * 开始记录，此后到 [[Timeline.submit]] 之间对时间轴的每次修改都会记录一条命令，
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
  def getTrackOrCreate(index: Int): Track = {
    val ts = tracks
    if (index < ts.size) {
      ts(index)
    } else {
      this.synchronized {
        var grown = tracks
        while (grown.size <= index) {
          grown = grown :+ Track(this, grown.size)
        }
        tracks = grown
        grown(index)
      }
    }
  }

  /** 已有轨道的条数。 */
  def getTrackCount: Int = tracks.size

  /** 指定索引的轨道线程，不存在则创建。 */
  private[cave] def getWorker(index: Int): TrackWorker = {
    var worker = workers.get(index)
    if (worker == null) {
      getTrackOrCreate(index) // 线程的 [[TrackWorker.gapFrame]] 需要一个轨道
      worker = new TrackWorker(this, index)
      workers.put(index, worker)
    }
    worker
  }

  override def toString: String = {
    val body = tracks.zipWithIndex
      .map((track, i) => System.lineSeparator() + "track#" + i + ":" + track)
      .mkString
    "Timeline:" + body
  }
  def getLength: Long = {
    if (tracks.isEmpty) 0L else tracks.iterator.map(_.length).max
  }
  def getTracks: util.List[Track] = {
    tracks.asJava
  }

  /**
   * 两个时间线相等，当且仅当每个轨道按索引逐位对应相等。
   * 缺失的轨道与空轨道视为相等（只允许尾部为空的差异）。
   */
  override def equals(o: Any): Boolean = {
    if (this.asInstanceOf[AnyRef] eq o.asInstanceOf[AnyRef]) {
      true
    } else o match {
      case other: Timeline =>
        val max = Math.max(tracks.size, other.tracks.size)
        (0 until max).forall { i =>
          val a = if (i < tracks.size) tracks(i) else null
          val b = if (i < other.tracks.size) other.tracks(i) else null
          Timeline.trackEquals(a, b)
        }
      case _ => false
    }
  }

  override def hashCode(): Int = {
    var h = 1
    for (track <- tracks) {
      if (!track.isEmpty) {
        h = 31 * h + track.hashCode()
      }
    }
    h
  }

  /** 迭代有元素的轨道。 */
  override def iterator(): util.Iterator[Track] = {
    new IteratorImpl()
  }

  class IteratorImpl extends util.Iterator[Track] {
    private final val snapshot: Vector[Track] = tracks
    private var index: Int = 0

    private def skipEmpty(): Unit = {
      while (index < snapshot.size && snapshot(index).isEmpty) {
        index += 1
      }
    }

    override def hasNext: Boolean = {
      skipEmpty()
      index < snapshot.size
    }

    override def next(): Track = {
      if (!hasNext) throw new util.NoSuchElementException()
      val t = snapshot(index)
      index += 1
      t
    }
  }

}

object Timeline {
  /** 记录句柄，用于 try-with-resources，[[close]] 即 [[Timeline.submit]]。 */
  trait Recording extends AutoCloseable {
    override def close(): Unit
  }

  private def trackEquals(a: Track, b: Track): Boolean = {
    if (a == null) b == null || b.isEmpty
    else if (b == null) a.isEmpty
    else a.equals(b)
  }
}
