package com.lomekwi.cave.timeline

import com.badlogic.gdx.Gdx
import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.pipeline.{GapFrame, Source}
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.timeline.UndoManager.{AddSegCommand, CompoundCommand, MergeableCommand, MoveSegsCommand, RemoveSegCommand, RemoveSegsCommand, ResizeSegsCommand, SplitSegCommand, TrackEdit, UndoableCommand}
import com.lomekwi.cave.timeline.playback.{PlayStateChangedEvent, RefreshRequestEvent, SeekEvent}
import com.lomekwi.cave.util.Duplicatable

import java.io.{ObjectInputStream, Serializable}
import java.util
import java.util.concurrent.{Future, Phaser}
import java.util.concurrent.locks.LockSupport

import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

/**
 * 时间线。它是这套结构里唯一可变的地方，轨道本身不可变，编辑产生新版本，
 * 由这里把当前版本指针换掉。所有读取方（界面、播放线程、导出）都从这里取最新版本，
 * 因此不需要对轨道加锁。
 *
 * 轨道存放在一个不可变向量里，替换整条向量后一次性发布。
 * 跨轨道的批量操作（整组平移、换轨）因此对读者是原子的，不会看到只做了一半的中间态。
 */
@SerialVersionUID(1L)
class Timeline(final val project: Project) extends Serializable with java.lang.Iterable[Track] with Duplicatable[Timeline] {
  @transient private var recording: Boolean = false
  @transient private var recorded: util.List[UndoableCommand] = new util.ArrayList[UndoableCommand]()
  /** 当前版本指针。整条替换后发布，读者要么看到替换前要么看到替换后的整体状态。 */
  @volatile private var tracks: Vector[Track] = Vector.empty[Track]
  /** 轨迹线程。按轨道索引唯一，因此轨道换版本时线程与 Phaser 都不受影响。 */
  @transient private var workers: util.Map[Integer, TrackWorker] = new util.HashMap[Integer, TrackWorker]()
  /** 分组注册表。组是跨轨道的，故不归属任何单条轨道。 */
  private final val groups: util.List[SourceGroup] = new util.ArrayList[SourceGroup]()

  private def readObject(in: ObjectInputStream): Unit = {
    in.defaultReadObject()
    recording = false
    recorded = new util.ArrayList[UndoableCommand]()
    workers = new util.HashMap[Integer, TrackWorker]()
  }

  /** 把 index 处的轨道替换为新版本，并唤醒其轨迹线程。 */
  protected[timeline] def setTrack(index: Int, track: Track): Unit = publish(Seq(index -> track))

  /** 一次性替换多条轨道。读者只会看到替换前或替换后的整体状态。 */
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

  def tryAdd(track: Track, source: Source[?], range: Interval, origin: Long): Long = {
    val current = getTrackOrCreate(track.index)
    val (next, shift) = current.tryAdd(source, range, origin)
    if (shift == 0) {
      setTrack(track.index, next)
      push(AddSegCommand(this, track.index, current, next))
    }
    shift
  }

  protected[timeline] def addOrThrow(track: Track, source: Source[?], range: Interval, origin: Long): Unit = {
    val current = getTrackOrCreate(track.index)
    val next = current.addOrThrow(source, range, origin)
    setTrack(track.index, next)
    push(AddSegCommand(this, track.index, current, next))
  }

  def remove(source: Source[?]): Unit = {
    val track = findTrackOf(source)
    if (track != null) {
      val group = getGroup(source)
      val next = track.remove(source)
      setTrack(track.index, next)
      push(RemoveSegCommand(this, track.index, track, next, source, group))
    }
  }

  def remove(sources: util.Collection[Source[?]]): Unit = {
    val working = mutable.HashMap.empty[Int, Track]
    def currentOf(i: Int): Track = working.getOrElse(i, tracks(i))
    val entries = new util.ArrayList[RemoveSegsCommand.RemoveEntry](sources.size())
    for (s <- sources.asScala) {
      val track = findTrackOf(s)
      if (track != null) {
        val group = getGroup(s)
        val before = currentOf(track.index)
        val next = before.remove(s)
        working.put(track.index, next)
        entries.add(RemoveSegsCommand.RemoveEntry(TrackEdit(track.index, before, next), s, group))
      }
    }
    if (!entries.isEmpty) {
      setTracks(working.toSeq)
      push(new RemoveSegsCommand(this, entries))
    }
  }

  def split(track: Track, time: Long): Unit = {
    val current = getTrackOrCreate(track.index)
    if (current.canSplit(time)) {
      val next = current.split(time)
      setTrack(track.index, next)
      push(SplitSegCommand(this, track.index, current, next))
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
    val indices = trackIndicesOf(sources)
    if (indices.isEmpty) return 0L

    val bound: Long = indices
      .map((i: Int) => { val t = tracks(i); if (end) t.probeSetEnd(sources, forward) else t.probeSetStart(sources, forward) })
      .reduce(Track.tighter)
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return 0L

    val edits = new util.ArrayList[TrackEdit]()
    for (i <- indices) {
      val before = tracks(i)
      val after = if (end) before.setEnd(sources, applied) else before.setStart(sources, applied)
      edits.add(TrackEdit(i, before, after))
    }
    setTracks(edits.asScala.map(e => e.index -> e.after).toSeq)
    push(new ResizeSegsCommand(this, edits))
    applied
  }

  /** 仅按时间平移源（轨道不变）。deltaTime 截断到最大可用量后应用，整组最多移到与障碍贴合。@return 实际应用的偏移量；0 表示未移动。 */
  def moveTime(sources: util.Collection[Source[?]], deltaTime: Long): Long = {
    if (deltaTime == 0 || sources.isEmpty) return 0L
    val forward = deltaTime > 0
    val indices = trackIndicesOf(sources)
    if (indices.isEmpty) return 0L

    val bound: Long = indices.map((i: Int) => tracks(i).probeMove(sources, forward)).reduce(Track.tighter)
    val applied = if (forward) Math.min(deltaTime, Math.max(bound, 0))
                  else Math.max(deltaTime, Math.min(bound, 0))
    if (applied == 0) return 0L

    val edits = new util.ArrayList[TrackEdit]()
    for (i <- indices) {
      val before = tracks(i)
      // 先把该轨道上要移动的源全部摘掉，再按新位置放回；中途状态不对外发布
      var next = before.removeAll(sources)
      for (s <- sources.asScala) {
        if (before.contains(s)) {
          next = next.addOrThrow(s, before.getRange(s).shift(applied), before.getOrigin(s) + applied)
        }
      }
      edits.add(TrackEdit(i, before, next))
    }
    setTracks(edits.asScala.map(e => e.index -> e.after).toSeq)
    push(new MoveSegsCommand(this, edits))
    applied
  }

  /**
   * 仅按轨道索引平移源（时间区间不变）。deltaTrack 会被同向截断到最大可用的
   * 轨道偏移后应用。从请求的目标轨道起沿该方向逐条回退，落在第一条整组可放置的
   * 轨道上（不反向、不超过请求量），保持组内成员相对间距。
   * @return 实际应用的轨道偏移；0 表示该方向无法移动，保持原位。
   */
  def moveTrack(sources: util.Collection[Source[?]], deltaTrack: Int): Int = {
    val applied = findPlaceableTrack(sources, deltaTrack)
    if (applied == 0) return 0

    /** 源、原轨道索引、目标轨道索引、原区间、原 origin */
    val moves = new util.ArrayList[(Source[?], Int, Int, Interval, Long)]()
    for (s <- sources.asScala) {
      val from = findTrackOf(s)
      if (from != null) {
        val to = getTrackOrCreate(from.index + applied)
        moves.add((s, from.index, to.index, from.getRange(s), from.getOrigin(s)))
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
    // 先全部摘除再全部放回。源可能在成员之间换轨，摘除不完全会让放置被自己挡住
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
    push(new MoveSegsCommand(this, edits))
    applied
  }

  /** 涉及到的轨道索引，按出现顺序去重。 */
  private def trackIndicesOf(sources: util.Collection[Source[?]]): mutable.LinkedHashSet[Int] = {
    val indices = mutable.LinkedHashSet.empty[Int]
    for (s <- sources.asScala) {
      val t = findTrackOf(s)
      if (t != null) indices.add(t.index)
    }
    indices
  }

  /**
   * 在 deltaTrack 方向上找出整组可放置的最大轨道偏移（带符号，绝对值 ≤ |deltaTrack|）。
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
      // 目标轨道尚不存在（索引 ≥ tracks.size）时视为空闲
      val target = minIdx + deltaTrack - step * (span - k)
      if (canPlaceGroupOnTrack(sources, target)) return step * k
      k -= 1
    }
    0
  }

  /**
   * 整组按统一偏移移动后，是否每个成员在各自目标轨道上都不与既有源冲突。
   * 目标轨道尚不存在（索引 ≥ tracks.size）时视为空闲。
   */
  private def canPlaceGroupOnTrack(sources: util.Collection[Source[?]], target: Int): Boolean = {
    if (target < 0) {
      false
    } else {
      var refIdx = findTrackOf(sources.iterator().next()).index
      for (s <- sources.asScala) refIdx = Math.min(refIdx, findTrackOf(s).index)
      sources.asScala.forall { s =>
        val from = findTrackOf(s)
        val ti = from.index + (target - refIdx)
        ti >= tracks.size || tracks(ti).isFree(from.getRange(s), sources)
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
    for (track <- tracks) {
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
    for (track <- tracks) {
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
   * 开始记录，此后到 {@link #submit()} 之间对时间轴的每次修改都会记录一条命令，
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

  /** 指定索引的轨迹线程，不存在则创建。 */
  private[timeline] def getWorker(index: Int): TrackWorker = {
    var worker = workers.get(index)
    if (worker == null) {
      getTrackOrCreate(index) // 线程的 gapFrame 需要一个轨道
      worker = new TrackWorker(index)
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
    if (tracks.isEmpty) 0L else tracks.iterator.map(_.getLength).max
  }
  def getTracks: util.List[Track] = {
    tracks.asJava
  }

  /**
   * 两个时间线相等，当且仅当每个轨道对应相等，按索引逐位比较轨道内容。
   * 由于 {@link #getTrack(int)} 会按需自动创建空轨道、而撤销不会删除轨道，
   * 比较时把"缺失"与"空轨道"视为相等（只允许尾部为空的差异）。
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

  /**
   * 迭代有元素的轨道
   * @return 轨道迭代器
   */
  override def iterator(): util.Iterator[Track] = {
    new IteratorImpl()
  }

  class IteratorImpl extends util.Iterator[Track] {
    /** 迭代期间轨道向量可能被换成新版本，取一次快照，保证一轮迭代看到的是同一批轨道。 */
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

  /**
   * 轨迹线程，按时间独立推进播放头，把帧投到项目事件总线上，
   * 并用 Phaser 与消费方（预览、音频混音）做握手。
   *
   * 它按轨道索引唯一、由时间线持有，轨道换版本时线程、Phaser 与注册的消费方
   * 都不必跟着换。内容则每轮从 {@link #tracks} 现取，拿到的一定是当前版本。
   */
  class TrackWorker(private val index: Int) extends Runnable {
    private final val gapFrame: GapFrame = new GapFrame(tracks(index))
    private var sinkPhaser: Phaser = uninitialized
    private var future: Future[?] = uninitialized
    @volatile private var workerThread: Thread = uninitialized
    @volatile private var updateNeeded: Boolean = false

    project.projEventBus.register(this)

    def getSinkPhaser: Phaser = {
      sinkPhaser
    }

    def getFuture: Future[?] = {
      future
    }

    def setFuture(future: Future[?]): Unit = {
      this.future = future
    }

    override def run(): Unit = {
      workerThread = Thread.currentThread()
      sinkPhaser = new Phaser(1)
      Gdx.app.log("Track" + index, "轨道线程启动: " + tracks(index))
      try {
        val p = project.playhead
        // 播放头当前所在的片段。播放头离开它时在该源上收尾。
        var activeSource: Source[?] = null
        var activeRange: Interval = null
        while (!Thread.currentThread().isInterrupted) {
          val track = tracks(index)
          var t: Long = p.getTime
          if (activeSource != null && !activeRange.contains(t)) {
            val out = activeSource
            activeSource = null
            activeRange = null
            out.onStepOut(t - track.getOrigin(out), track)
          }
          if (!p.isPlaying) {
            Gdx.app.debug("Track" + index, "因为播放头而尝试park...")

            var f: com.lomekwi.cave.pipeline.Frame = null
            track.get(t) match {
              case Segment(source) =>
                track.syncAt(source, t)
                activeSource = source
                activeRange = track.getRange(source)
                f = track.frameAt(source, t)
              case _: Gap =>
            }
            project.projEventBus.post(util.Objects.requireNonNullElse(f, gapFrame))

            LockSupport.park()
          } else {
            updateNeeded = false
            track.get(t) match {
              case Segment(source) =>
                val r = track.getRange(source)
                Gdx.app.debug("Track" + index, "找到源: " + source)
                track.syncAt(source, t)
                activeSource = source
                activeRange = r
                val end: Long = r.hi
                while (t < end && !updateNeeded && !Thread.currentThread().isInterrupted) {
                  t = project.playhead.getTime
                  val frame = track.frameAt(source, t)
                  if (!updateNeeded && frame != null) {
                    project.projEventBus.post(frame)
                    val phase = sinkPhaser.arrive()
                    try {
                      sinkPhaser.awaitAdvanceInterruptibly(phase)
                    } catch {
                      case _: InterruptedException =>
                        Thread.currentThread().interrupt()
                    }
                  }
                }
              case _: Gap =>
                project.projEventBus.post(gapFrame)
                var parkTime: Long = Long.MaxValue
                val next = track.sourceAtOrAfter(t)
                if (next != null) {
                  parkTime = track.getRange(next).lo - t
                  parkTime *= 1000
                  parkTime = Math.max(parkTime, 1)
                }
                Gdx.app.debug("Track" + index, "轨道线程等待: " + parkTime / 1e9 + "秒")
                LockSupport.parkNanos(parkTime)
            }
          }
        }
      } catch {
        case e: Exception =>
          if (!e.isInstanceOf[InterruptedException]) {
            Gdx.app.error("Track" + index, "在更新轨道时发生错误", e)
            Gdx.app.postRunnable(() => {
              throw new RuntimeException(e)
            })
          }
      } finally {
        workerThread = null
        Gdx.app.log("Track" + index, "轨道线程结束: " + tracks(index))
      }
    }
    @Subscribe
    def onPlayStateChanged(event: PlayStateChangedEvent): Unit = {
      update()
    }
    @Subscribe
    def onRefreshRequested(event: RefreshRequestEvent): Unit = {
      update()
    }
    @Subscribe
    def onSeek(event: SeekEvent): Unit = {
      update()
    }
    protected[timeline] def onTrackChanged(): Unit = {
      update()
    }
    private def update(): Unit = {
      val t = workerThread
      if (t != null) {
        LockSupport.unpark(t)
      }
      updateNeeded = true
    }
  }
}

object Timeline {
  /** 记录句柄，用于 try-with-resources，close() 即 {@link #submit()}。 */
  trait Recording extends AutoCloseable {
    override def close(): Unit
  }

  private def trackEquals(a: Track, b: Track): Boolean = {
    if (a == null) b == null || b.isEmpty
    else if (b == null) a.isEmpty
    else a.equals(b)
  }
}
