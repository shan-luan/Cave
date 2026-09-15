package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.image.TransNode
import com.lomekwi.cave.pipeline.num.NumFrame
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.project.ProjectDirtyChangedEvent
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent


import scala.jdk.CollectionConverters.*
import java.util

class UndoManager(@transient private val project: Project) {
  private final val undoStack: util.Deque[UndoManager.UndoableCommand] = new util.ArrayDeque[UndoManager.UndoableCommand]()
  private final val redoStack: util.Deque[UndoManager.UndoableCommand] = new util.ArrayDeque[UndoManager.UndoableCommand]()

  def execute(command: UndoManager.UndoableCommand): Unit = {
    val wasDirty = project.isDirty
    project.currentVersion = project.currentVersion + 1
    command.redo()
    push(command)
    if (wasDirty != project.isDirty) {
      project.projEventBus.post(ProjectDirtyChangedEvent)
    }
  }

  def record(command: UndoManager.UndoableCommand): Unit = {
    val wasDirty = project.isDirty
    project.currentVersion = project.currentVersion + 1
    push(command)
    if (wasDirty != project.isDirty) {
      project.projEventBus.post(ProjectDirtyChangedEvent)
    }
  }

  private def push(command: UndoManager.UndoableCommand): Unit = {
    // 与栈顶同类型的可合并命令直接合并
    val top = if (undoStack.isEmpty) null else undoStack.peek()
    val merged = (command, top) match {
      case (_: UndoManager.MergeableCommand, topMc: UndoManager.MergeableCommand) =>
        topMc.getClass == command.getClass && topMc.merge(command)
      case _ => false
    }
    if (!merged) {
      undoStack.push(command)
    }
    redoStack.clear()
    if (undoStack.size() > UndoManager.MAX_UNDO) {
      undoStack.removeLast()
    }
  }

  def undo(): Unit = {
    if (!undoStack.isEmpty) {
      val wasDirty = project.isDirty
      project.currentVersion = project.currentVersion - 1
      val command = undoStack.pop()
      command.undo()
      redoStack.push(command)
      if (wasDirty != project.isDirty) {
        project.projEventBus.post(ProjectDirtyChangedEvent)
      }
    }
  }

  def redo(): Unit = {
    if (!redoStack.isEmpty) {
      val wasDirty = project.isDirty
      project.currentVersion = project.currentVersion + 1
      val command = redoStack.pop()
      command.redo()
      undoStack.push(command)
      if (wasDirty != project.isDirty) {
        project.projEventBus.post(ProjectDirtyChangedEvent)
      }
    }
  }

  def canUndo: Boolean = !undoStack.isEmpty

  def canRedo: Boolean = !redoStack.isEmpty

  def clear(): Unit = {
    undoStack.clear()
    redoStack.clear()
  }
}

object UndoManager {
  private final val MAX_UNDO = 100

  trait UndoableCommand {
    def undo(): Unit
    def redo(): Unit
  }

  /**
   * 可合并命令：同类型命令可合并为一个，避免栈中存在连续的同类型记录。
   */
  trait MergeableCommand extends UndoableCommand {
    /**
     * 将 other 合并到当前命令中。合并后，undo() 应能撤销两者的效果，
     * redo() 应能重做合并后的效果。
     * @return true 表示合并成功
     */
    def merge(other: UndoableCommand): Boolean
  }

  // ──────────────── 单片段命令（保留向后兼容） ────────────────

  case class AddSegCommand(track: Track, segment: Segment, range: Interval) extends UndoableCommand {
    override def undo(): Unit = {
      track.remove(segment)
    }

    override def redo(): Unit = {
      track.`override`(segment, range)
    }
  }

  case class RemoveSegCommand(track: Track, segment: Segment, range: Interval, group: SegmentGroup) extends UndoableCommand {
    def this(track: Track, segment: Segment, range: Interval) = {
      this(track, segment, range, null)
    }

    override def undo(): Unit = {
      track.`override`(segment, range)
      if (group != null) group.add(segment)
    }

    override def redo(): Unit = {
      track.remove(segment)
      if (group != null) group.remove(segment)
    }
  }

  case class ResizeSegCommand(track: Track, segment: Segment, oldRange: Interval, newRange: Interval) extends UndoableCommand {
    override def undo(): Unit = {
      track.remove(segment)
      track.`override`(segment, oldRange)
    }

    override def redo(): Unit = {
      track.remove(segment)
      track.`override`(segment, newRange)
    }
  }

  case class MoveSegCommand(fromTrack: Track, toTrack: Track, segment: Segment, oldRange: Interval, newRange: Interval) extends UndoableCommand {
    override def undo(): Unit = {
      toTrack.remove(segment)
      fromTrack.`override`(segment, oldRange)
      segment.offsetOrigin(oldRange.lo - newRange.lo)
    }

    override def redo(): Unit = {
      fromTrack.remove(segment)
      toTrack.`override`(segment, newRange)
      segment.offsetOrigin(newRange.lo - oldRange.lo)
    }
  }

  case class SplitSegCommand(track: Track, originalSeg: Segment, originalRange: Interval, newSeg: Segment, splitTime: Long) extends UndoableCommand {
    override def undo(): Unit = {
      track.remove(originalSeg)
      track.remove(newSeg)
      track.`override`(originalSeg, originalRange)
    }

    override def redo(): Unit = {
      track.remove(originalSeg)
      track.remove(newSeg)
      track.`override`(originalSeg, Interval(originalRange.lo, splitTime))
      track.`override`(newSeg, Interval(splitTime, originalRange.hi))
    }
  }

  class CompoundCommand(commands: UndoableCommand*) extends UndoableCommand {
    override def undo(): Unit = {
      commands.reverseIterator.foreach(cmd => cmd.undo())
    }

    override def redo(): Unit = {
      for (cmd <- commands) {
        cmd.redo()
      }
    }
  }

  private def filterList(source: Source[?]): util.List[Filter[?]] = {
    source.getFilters.asInstanceOf[util.List[Filter[?]]]
  }

  // ──────────────── 批量命令（可合并） ────────────────

  /** 批量移动片段命令。合并时：同 segment 保留旧起点、更新终点；新 segment 直接追加。 */
  final class MoveSegsCommand(entries0: util.List[MoveSegsCommand.MoveEntry]) extends MergeableCommand {
    private final val entries: util.List[MoveSegsCommand.MoveEntry] = new util.ArrayList[MoveSegsCommand.MoveEntry](entries0)

    override def undo(): Unit = {
      entries.asScala.reverseIterator.foreach { e =>
        e.toTrack.remove(e.segment)
        e.fromTrack.`override`(e.segment, e.oldRange)
        e.segment.offsetOrigin(e.oldRange.lo - e.newRange.lo)
      }
    }

    override def redo(): Unit = {
      for (e <- entries.asScala) {
        e.fromTrack.remove(e.segment)
        e.toTrack.`override`(e.segment, e.newRange)
        e.segment.offsetOrigin(e.newRange.lo - e.oldRange.lo)
      }
    }

    override def merge(other: UndoableCommand): Boolean = {
      other match {
        case o: MoveSegsCommand =>
          for (ne <- o.entries.asScala) {
            val idx = entries.asScala.indexWhere(e => e.segment eq ne.segment)
            if (idx < 0) {
              entries.add(ne)
            } else {
              val e = entries.get(idx)
              entries.set(idx, MoveSegsCommand.MoveEntry(e.fromTrack, ne.toTrack, e.segment,
                e.oldRange, ne.newRange))
            }
          }
          true
        case _ =>
          false
      }
    }
  }

  object MoveSegsCommand {
    case class MoveEntry(fromTrack: Track, toTrack: Track, segment: Segment,
                         oldRange: Interval, newRange: Interval)
  }

  /** 批量调整片段区间命令。合并时：同 segment 保留旧区间、更新新区间；新 segment 直接追加。 */
  final class ResizeSegsCommand(entries0: util.List[ResizeSegsCommand.ResizeEntry]) extends MergeableCommand {
    private final val entries: util.List[ResizeSegsCommand.ResizeEntry] = new util.ArrayList[ResizeSegsCommand.ResizeEntry](entries0)

    override def undo(): Unit = {
      entries.asScala.reverseIterator.foreach { e =>
        e.track.remove(e.segment)
        e.track.`override`(e.segment, e.oldRange)
      }
    }

    override def redo(): Unit = {
      for (e <- entries.asScala) {
        e.track.remove(e.segment)
        e.track.`override`(e.segment, e.newRange)
      }
    }

    override def merge(other: UndoableCommand): Boolean = {
      other match {
        case o: ResizeSegsCommand =>
          for (ne <- o.entries.asScala) {
            val idx = entries.asScala.indexWhere(e => e.segment eq ne.segment)
            if (idx < 0) {
              entries.add(ne)
            } else {
              val e = entries.get(idx)
              entries.set(idx, ResizeSegsCommand.ResizeEntry(e.track, e.segment,
                e.oldRange, ne.newRange))
            }
          }
          true
        case _ =>
          false
      }
    }
  }

  object ResizeSegsCommand {
    case class ResizeEntry(track: Track, segment: Segment,
                           oldRange: Interval, newRange: Interval)
  }

  /** 批量删除片段命令。合并时直接追加新条目（去重）。 */
  final class RemoveSegsCommand(entries0: util.List[RemoveSegsCommand.RemoveEntry]) extends MergeableCommand {
    private final val entries: util.List[RemoveSegsCommand.RemoveEntry] = new util.ArrayList[RemoveSegsCommand.RemoveEntry](entries0)

    override def undo(): Unit = {
      entries.asScala.reverseIterator.foreach { e =>
        e.track.`override`(e.segment, e.range)
        if (e.group != null) e.group.add(e.segment)
      }
    }

    override def redo(): Unit = {
      for (e <- entries.asScala) {
        e.track.remove(e.segment)
        if (e.group != null) e.group.remove(e.segment)
      }
    }

    override def merge(other: UndoableCommand): Boolean = {
      other match {
        case o: RemoveSegsCommand =>
          for (ne <- o.entries.asScala) {
            if (!entries.asScala.exists(e => e.segment eq ne.segment)) entries.add(ne)
          }
          true
        case _ =>
          false
      }
    }
  }

  object RemoveSegsCommand {
    case class RemoveEntry(track: Track, segment: Segment, range: Interval,
                           group: SegmentGroup)
  }

  private def postRefresh(source: Source[?]): Unit = {
    val seg: Segment = source.getSegment
    if (seg != null) {
      val track: Track = seg.getTrack
      if (track != null) {
        val timeline: Timeline = track.getTimeline
        timeline.project.projEventBus.post(SegmentSelectedEvent(seg, track, 1))
        timeline.project.projEventBus.post(RefreshRequestEvent)
      }
    }
  }

  case class AddFilterCommand(source: Source[?], filter: Filter[?]) extends UndoableCommand {
    override def undo(): Unit = {
      filterList(source).remove(filter)
      postRefresh(source)
    }

    override def redo(): Unit = {
      filterList(source).add(filter)
      postRefresh(source)
    }
  }

  case class RemoveFilterCommand(source: Source[?], filter: Filter[?], index: Int) extends UndoableCommand {
    override def undo(): Unit = {
      filterList(source).add(index, filter)
      postRefresh(source)
    }

    override def redo(): Unit = {
      filterList(source).remove(filter)
      postRefresh(source)
    }
  }

  case class ReorderFilterCommand(source: Source[?], filter: Filter[?], oldIndex: Int, newIndex: Int) extends UndoableCommand {
    override def undo(): Unit = {
      filterList(source).remove(filter)
      filterList(source).add(oldIndex, filter)
      postRefresh(source)
    }

    override def redo(): Unit = {
      filterList(source).remove(filter)
      filterList(source).add(newIndex, filter)
      postRefresh(source)
    }
  }

  /** 数值输入端口默认值变更命令。 */
  case class NumPortValueCommand(port: Node.InPort[?], source: Source[?], oldValue: Double, newValue: Double) extends UndoableCommand {
    override def undo(): Unit = {
      setValue(oldValue)
    }

    override def redo(): Unit = {
      setValue(newValue)
    }

    private def setValue(v: Double): Unit = {
      val `def`: NumFrame = port.getDefaultData.asInstanceOf[NumFrame]
      if (`def` != null) `def`.setVal(v)
      else port.getData.asInstanceOf[NumFrame].setVal(v)
      // 通知所属源刷新：命令创建时端口所属节点为 Source，或挂载在 Source 链上的 Filter
      if (source != null) postRefresh(source)
    }
  }

  case class TransNodeState(dx: Float, dy: Float, scaleX: Float, scaleY: Float,
                            dRotation: Float,
                            flipX: Boolean, flipY: Boolean)

  case class TransformNodeCommand(source: Source[?], node: TransNode, oldState: TransNodeState, newState: TransNodeState) extends UndoableCommand {
    override def undo(): Unit = {
      applyState(oldState)
    }

    override def redo(): Unit = {
      applyState(newState)
    }

    private def applyState(s: TransNodeState): Unit = {
      node.setDx(s.dx)
      node.setDy(s.dy)
      node.setScaleX(s.scaleX)
      node.setScaleY(s.scaleY)
      node.setDRotation(s.dRotation)
      node.flipX(s.flipX)
      node.flipY(s.flipY)
      if (source != null) postRefresh(source)
    }
  }
}
