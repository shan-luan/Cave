package com.lomekwi.cave.timeline

import com.google.common.collect.Range
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.timeline.UndoManager.{AddSegCommand, CompoundCommand, MergeableCommand, MoveSegsCommand, RemoveSegCommand, RemoveSegsCommand, ResizeSegsCommand, SplitSegCommand, UndoableCommand}
import com.lomekwi.cave.util.Duplicatable

import com.lomekwi.cave.util.Ranges.shift

import java.io.{IOException, ObjectInputStream, Serializable}
import java.util.{ArrayList, Collection, HashMap, HashSet, Iterator, List, Map, Set}

import scala.jdk.CollectionConverters.*

@SerialVersionUID(1L)
class Timeline(final val project: Project) extends Serializable with java.lang.Iterable[Track] with Duplicatable[Timeline] {
  @transient private var recording: Boolean = false
  @transient private var recorded: List[UndoableCommand] = new ArrayList[UndoableCommand]()
  private final val tracks: List[Track] = new ArrayList[Track]()
  private var length: Long = 0L
  private var lengthChanged: Boolean = true

  private def readObject(in: ObjectInputStream): Unit = {
    in.defaultReadObject()
    for (track <- tracks.asScala) {
      track.setTimeline(this)
    }
    recording = false
    recorded = new ArrayList[UndoableCommand]()
  }

  def tryAdd(track: Track, segment: Segment, range: Range[java.lang.Long]): Long = {
    val shift = track.tryAdd(segment, range)
    if (shift == 0) {
      push(new AddSegCommand(track, segment, range))
    }
    shift
  }
  protected[timeline] def `override`(track: Track, segment: Segment, range: Range[java.lang.Long]): Unit = {
    track.`override`(segment, range)
    push(new AddSegCommand(track, segment, range))
  }
  def remove(segment: Segment): Unit = {
    val track = segment.getTrack()
    val range = segment.getRange()
    if (track != null && range != null && track.remove(segment)) {
      push(new RemoveSegCommand(track, segment, range, segment.getGroup()))
    }
  }
  def remove(segments: Collection[Segment]): Unit = {
    val entries: List[RemoveSegsCommand.RemoveEntry] = new ArrayList[RemoveSegsCommand.RemoveEntry](segments.size())
    for (s <- segments.asScala) {
      val track = s.getTrack()
      val range = s.getRange()
      if (track != null && range != null && track.remove(s)) {
        entries.add(new RemoveSegsCommand.RemoveEntry(track, s, range, s.getGroup()))
      }
    }
    if (!entries.isEmpty()) {
      push(new RemoveSegsCommand(entries))
    }
  }

  def split(track: Track, time: Long): Unit = {
    val s = track.get(time)
    if (s == null) return
    val r = s.getRange()
    val lo: Long = r.lowerEndpoint()
    val hi: Long = r.upperEndpoint()
    if (time <= lo || time >= hi) return
    if (track.split(time)) {
      val right = track.get(time)
      push(new SplitSegCommand(track, s, r, right, time))
    }
  }
  def split(time: Long): Unit = {
    for (t <- tracks.asScala) {
      split(t, time)
    }
  }

  /** 裁切一组片段的起始边缘（各自终点不变）。@return 实际应用的偏移量（截断到最大可用量），0 表示未移动。 */
  def setStart(segments: Collection[Segment], deltaTime: Long): Long = {
    applyPerTrack(segments, deltaTime, false)
  }

  /** 裁切一组片段的结束边缘（各自起点不变）。@return 同 {@link #setStart}。 */
  def setEnd(segments: Collection[Segment], deltaTime: Long): Long = {
    applyPerTrack(segments, deltaTime, true)
  }

  /** 各轨道 probe 后取限制最严者，把 deltaTime 同向截断并应用。@return 实际应用的偏移量，0 表示未移动。 */
  private def applyPerTrack(segments: Collection[Segment], deltaTime: Long, end: Boolean): Long = {
    if (deltaTime == 0 || segments.isEmpty()) return 0
    val forward = deltaTime > 0
    val tracks: Set[Track] = new HashSet[Track]()
    for (s <- segments.asScala) if (s.getTrack() != null) tracks.add(s.getTrack())
    if (tracks.isEmpty()) return 0

    val bound: Long = tracks.stream()
      .mapToLong((track: Track) => if (end) track.probeSetEnd(segments, forward)
                                    else track.probeSetStart(segments, forward))
      .reduce(if (forward) Long.MaxValue else Long.MinValue, Track.tighter)
    // 夹紧到与请求同向且不超过请求量
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return 0

    // 捕获旧区间 → 修改 → 记录
    val before: Map[Segment, Range[java.lang.Long]] = new HashMap[Segment, Range[java.lang.Long]]()
    for (s <- segments.asScala) before.put(s, s.getRange())
    for (track <- tracks.asScala) {
      if (end) track.setEnd(segments, applied)
      else track.setStart(segments, applied)
    }
    val entries: List[ResizeSegsCommand.ResizeEntry] = new ArrayList[ResizeSegsCommand.ResizeEntry]()
    for (s <- segments.asScala) {
      val track = s.getTrack()
      val old = before.get(s)
      val r = s.getRange()
      if (track != null && old != null && r != null && !old.equals(r)) {
        entries.add(new ResizeSegsCommand.ResizeEntry(track, s, old, r))
      }
    }
    if (!entries.isEmpty()) {
      push(new ResizeSegsCommand(entries))
    }
    applied
  }
  /** 仅按时间平移片段（轨道不变）。deltaTime 截断到最大可用量后应用：整组最多移到与障碍贴合。@return 实际应用的偏移量；0 表示未移动。 */
  def moveTime(segments: Collection[Segment], deltaTime: Long): Long = {
    if (deltaTime == 0 || segments.isEmpty()) return 0
    val forward = deltaTime > 0
    val tracks: Set[Track] = new HashSet[Track]()
    for (s <- segments.asScala) if (s.getTrack() != null) tracks.add(s.getTrack())
    if (tracks.isEmpty()) return 0

    val bound: Long = tracks.stream()
      .mapToLong((tr: Track) => tr.probeMove(segments, forward))
      .reduce(if (forward) Long.MaxValue else Long.MinValue, Track.tighter)
    // 防御性夹紧：同向且不超过请求量
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return 0

    // 先构造命令再移动（移除直接走 Track，避免重复记录）
    val entries: List[MoveSegsCommand.MoveEntry] = new ArrayList[MoveSegsCommand.MoveEntry](segments.size())
    for (s <- segments.asScala) {
      val r = s.getRange()
      entries.add(new MoveSegsCommand.MoveEntry(s.getTrack(), s.getTrack(), s, r, shift(r, applied)))
    }
    for (s <- segments.asScala) {
      val t = s.getTrack()
      if (t != null) t.remove(s)
    }
    for (s <- segments.asScala) {
      val tr = s.getTrack()
      tr.`override`(s, shift(s.getRange(), applied))
      s.offsetOrigin(applied)
    }
    push(new MoveSegsCommand(entries))
    applied
  }

  /**
   * 仅按轨道索引平移片段（时间区间不变）。deltaTrack 会被同向截断到最大可用的
   * 轨道偏移后应用：从请求的目标轨道起沿该方向逐条回退，落在第一条整组可放置的
   * 轨道上（不反向、不超过请求量），保持组内成员相对间距。
   * @return 实际应用的轨道偏移；0 表示该方向无法移动，保持原位。
   */
  def moveTrack(segments: Collection[Segment], deltaTrack: Int): Int = {
    val applied = findPlaceableTrack(segments, deltaTrack)
    // applied 即本次实际落位的轨道偏移（0 表示不动）
    if (applied == 0) return 0

    val entries: List[MoveSegsCommand.MoveEntry] = new ArrayList[MoveSegsCommand.MoveEntry](segments.size())
    for (s <- segments.asScala) {
      val from = s.getTrack()
      val to = getTrack(from.index + applied)
      if (!from.eq(to)) {
        entries.add(new MoveSegsCommand.MoveEntry(from, to, s, s.getRange(), s.getRange()))
      }
    }
    for (s <- segments.asScala) {
      val t = s.getTrack()
      if (t != null) t.remove(s)
    }
    for (s <- segments.asScala) {
      getTrack(s.getTrack().index + applied).`override`(s, s.getRange())
    }
    if (!entries.isEmpty()) {
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
  private def findPlaceableTrack(segments: Collection[Segment], deltaTrack: Int): Int = {
    if (deltaTrack == 0 || segments.isEmpty()) return 0
    val minIdx = segments.stream().mapToInt((s: Segment) => s.getTrack().index).min().orElseThrow()
    val step = if (deltaTrack > 0) 1 else -1
    val span = Math.abs(deltaTrack)
    var k = span
    while (k > 0) {
      // 目标轨道尚不存在（索引 ≥ tracks.size()）时视为空闲
      val target = minIdx + deltaTrack - step * (span - k)
      if (canPlaceGroupOnTrack(segments, target)) return step * k
      k -= 1
    }
    0
  }

  /**
   * 整组按统一偏移移动后，是否每个成员在各自目标轨道上都不与既有片段冲突。
   * 目标轨道尚不存在（索引 ≥ tracks.size()）时视为空闲。
   */
  private def canPlaceGroupOnTrack(segments: Collection[Segment], target: Int): Boolean = {
    if (target < 0) return false
    var refIdx = segments.iterator().next().getTrack().index
    for (s <- segments.asScala) refIdx = Math.min(refIdx, s.getTrack().index)
    val it = segments.iterator()
    while (it.hasNext) {
      val s = it.next()
      val ti = s.getTrack().index + (target - refIdx)
      if (ti < tracks.size() && !tracks.get(ti).isFree(s.getRange(), segments)) return false
    }
    true
  }
  /** 在 [time±threshold] 内扫描所有轨道片段，返回最近的起点/终点（无则原值）；ignore 不参与。 */
  def snapTime(time: Long, threshold: Long, ignore: Collection[Segment]): Long = {
    var best = time
    var bestDist = threshold
    val searchStart: Long = Math.max(0, time - threshold)
    val searchEnd: Long = time + threshold
    if (searchEnd <= searchStart) return time
    val searchRange: Range[java.lang.Long] = Range.closedOpen(java.lang.Long.valueOf(searchStart), java.lang.Long.valueOf(searchEnd))
    for (track <- tracks.asScala) {
      for (entry <- track.getSubRangeMapAsEntrySet(searchRange).asScala) {
        if (!ignore.contains(entry.getValue())) {
          val r = entry.getKey()
          var dist = Math.abs(r.lowerEndpoint() - time)
          if (dist < bestDist) {
            best = r.lowerEndpoint()
            bestDist = dist
          }
          dist = Math.abs(r.upperEndpoint() - time)
          if (dist < bestDist) {
            best = r.upperEndpoint()
            bestDist = dist
          }
        }
      }
    }
    // 距 0 比当前最佳吸附点更近时吸附到 0
    if (time < threshold && time < bestDist) {
      best = 0
    }
    best
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
    if (!recording) return
    recording = false
    if (recorded.isEmpty()) return
    if (recorded.size() == 1) {
      project.undoManager.record(recorded.get(0))
    } else {
      val arr = recorded.toArray(new Array[UndoableCommand](0))
      project.undoManager.record(new CompoundCommand(arr*))
    }
    recorded.clear()
  }
  /** 记录模式下把一次修改对应的命令压入记录栈。同类型命令会与栈尾合并。 */
  private def push(command: UndoableCommand): Unit = {
    if (!recording) return
    // 与 recording 栈中最近命令合并
    if (!recorded.isEmpty() && command.isInstanceOf[MergeableCommand]) {
      val last = recorded.get(recorded.size() - 1)
      if (last.getClass() == command.getClass()) {
        val lm = last.asInstanceOf[MergeableCommand]
        if (lm.merge(command)) return
      }
    }
    recorded.add(command)
  }

  /**
   * 获取指定索引的轨道，如果不存在则自动创建
   * @param index 轨道索引
   * @return 对应的轨道对象
   */
  def getTrack(index: Int): Track = {
    while (tracks.size() <= index) {
      tracks.add(new Track(this, tracks.size()))
    }
    tracks.get(index)
  }

  override def toString(): String = {
    val sb = new StringBuilder()
    sb.append("Timeline:")
    var i = 0
    while (i < tracks.size()) {
      sb.append(System.lineSeparator())
      sb.append("track#").append(i).append(":").append(tracks.get(i))
      i += 1
    }
    sb.toString()
  }
  def getLength(): Long = {
    if (lengthChanged) {
      length = tracks.stream()
        .mapToLong((t: Track) => t.getLength())
        .max()
        .orElse(0)
      lengthChanged = false
    }
    length
  }
  def getTracks(): List[Track] = {
    tracks
  }

  /**
   * 两个时间线相等，当且仅当每个轨道对应相等：按索引逐位比较轨道内容。
   * 由于 {@link #getTrack(int)} 会按需自动创建空轨道、而撤销不会删除轨道，
   * 比较时把"缺失"与"空轨道"视为相等（只允许尾部为空的差异）。
   */
  override def equals(o: Any): Boolean = {
    if (this.asInstanceOf[AnyRef] eq o.asInstanceOf[AnyRef]) return true
    o match {
      case other: Timeline =>
        val max = Math.max(tracks.size(), other.tracks.size())
        var i = 0
        while (i < max) {
          val a = if (i < tracks.size()) tracks.get(i) else null
          val b = if (i < other.tracks.size()) other.tracks.get(i) else null
          if (!Timeline.trackEquals(a, b)) return false
          i += 1
        }
        true
      case _ => false
    }
  }

  override def hashCode(): Int = {
    var h = 1
    for (track <- tracks.asScala) {
      if (!track.isEmpty()) {
        h = 31 * h + track.hashCode()
      }
    }
    h
  }

  /**
   * 迭代有元素的轨道
   * @return 轨道迭代器
   */
  override def iterator(): Iterator[Track] = {
    new IteratorImpl()
  }

  class IteratorImpl extends Iterator[Track] {
    private var index: Int = 0

    private def skipEmpty(): Unit = {
      while (index < tracks.size() && tracks.get(index).isEmpty()) {
        index += 1
      }
    }

    override def hasNext(): Boolean = {
      skipEmpty()
      index < tracks.size()
    }

    override def next(): Track = {
      if (!hasNext()) throw new java.util.NoSuchElementException()
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
    if (a == null) return b == null || b.isEmpty()
    if (b == null) return a.isEmpty()
    a.equals(b)
  }
}
