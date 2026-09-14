package com.lomekwi.cave.timeline

import com.google.common.collect.Range
import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.image.TransNode
import com.lomekwi.cave.pipeline.num.NumFrame
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.project.ProjectDirtyChangedEvent
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent

import java.util.{ArrayDeque, ArrayList, Deque, List}

import scala.jdk.CollectionConverters.*

class UndoManager(@transient private val project: Project) {
  private final val undoStack: Deque[UndoManager.UndoableCommand] = new ArrayDeque[UndoManager.UndoableCommand]()
  private final val redoStack: Deque[UndoManager.UndoableCommand] = new ArrayDeque[UndoManager.UndoableCommand]()

  def execute(command: UndoManager.UndoableCommand): Unit = {
    val wasDirty = project.isDirty()
    project.currentVersion = project.currentVersion + 1
    command.redo()
    push(command)
    if (wasDirty != project.isDirty()) {
      project.projEventBus.post(ProjectDirtyChangedEvent.INSTANCE)
    }
  }

  def record(command: UndoManager.UndoableCommand): Unit = {
    val wasDirty = project.isDirty()
    project.currentVersion = project.currentVersion + 1
    push(command)
    if (wasDirty != project.isDirty()) {
      project.projEventBus.post(ProjectDirtyChangedEvent.INSTANCE)
    }
  }

  private def push(command: UndoManager.UndoableCommand): Unit = {
    // 与栈顶同类型的可合并命令直接合并
    if (!undoStack.isEmpty) {
      command match {
        case _: UndoManager.MergeableCommand =>
          val top = undoStack.peek()
          top match {
            case topMc: UndoManager.MergeableCommand if top.getClass == command.getClass =>
              if (topMc.merge(command)) {
                redoStack.clear()
                if (undoStack.size() > UndoManager.MAX_UNDO) {
                  undoStack.removeLast()
                }
                return
              }
            case _ =>
          }
        case _ =>
      }
    }
    undoStack.push(command)
    redoStack.clear()
    if (undoStack.size() > UndoManager.MAX_UNDO) {
      undoStack.removeLast()
    }
  }

  def undo(): Unit = {
    if (undoStack.isEmpty) return
    val wasDirty = project.isDirty()
    project.currentVersion = project.currentVersion - 1
    val command = undoStack.pop()
    command.undo()
    redoStack.push(command)
    if (wasDirty != project.isDirty()) {
      project.projEventBus.post(ProjectDirtyChangedEvent.INSTANCE)
    }
  }

  def redo(): Unit = {
    if (redoStack.isEmpty) return
    val wasDirty = project.isDirty()
    project.currentVersion = project.currentVersion + 1
    val command = redoStack.pop()
    command.redo()
    undoStack.push(command)
    if (wasDirty != project.isDirty()) {
      project.projEventBus.post(ProjectDirtyChangedEvent.INSTANCE)
    }
  }

  def canUndo(): Boolean = !undoStack.isEmpty

  def canRedo(): Boolean = !redoStack.isEmpty

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

  case class AddSegCommand(track: Track, segment: Segment, range: Range[java.lang.Long]) extends UndoableCommand {
    override def undo(): Unit = {
      track.remove(segment)
    }

    override def redo(): Unit = {
      track.`override`(segment, range)
    }
  }

  case class RemoveSegCommand(track: Track, segment: Segment, range: Range[java.lang.Long], group: SegmentGroup) extends UndoableCommand {
    def this(track: Track, segment: Segment, range: Range[java.lang.Long]) = {
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

  case class ResizeSegCommand(track: Track, segment: Segment, oldRange: Range[java.lang.Long], newRange: Range[java.lang.Long]) extends UndoableCommand {
    override def undo(): Unit = {
      track.remove(segment)
      track.`override`(segment, oldRange)
    }

    override def redo(): Unit = {
      track.remove(segment)
      track.`override`(segment, newRange)
    }
  }

  case class MoveSegCommand(fromTrack: Track, toTrack: Track, segment: Segment, oldRange: Range[java.lang.Long], newRange: Range[java.lang.Long]) extends UndoableCommand {
    override def undo(): Unit = {
      toTrack.remove(segment)
      fromTrack.`override`(segment, oldRange)
      segment.offsetOrigin(oldRange.lowerEndpoint() - newRange.lowerEndpoint())
    }

    override def redo(): Unit = {
      fromTrack.remove(segment)
      toTrack.`override`(segment, newRange)
      segment.offsetOrigin(newRange.lowerEndpoint() - oldRange.lowerEndpoint())
    }
  }

  case class SplitSegCommand(track: Track, originalSeg: Segment, originalRange: Range[java.lang.Long], newSeg: Segment, splitTime: Long) extends UndoableCommand {
    override def undo(): Unit = {
      track.remove(originalSeg)
      track.remove(newSeg)
      track.`override`(originalSeg, originalRange)
    }

    override def redo(): Unit = {
      track.remove(originalSeg)
      track.remove(newSeg)
      track.`override`(originalSeg, Range.closedOpen(originalRange.lowerEndpoint(), java.lang.Long.valueOf(splitTime)))
      track.`override`(newSeg, Range.closedOpen(java.lang.Long.valueOf(splitTime), originalRange.upperEndpoint()))
    }
  }

  class CompoundCommand(commands: UndoableCommand*) extends UndoableCommand {
    override def undo(): Unit = {
      var i = commands.length - 1
      while (i >= 0) {
        commands(i).undo()
        i -= 1
      }
    }

    override def redo(): Unit = {
      for (cmd <- commands) {
        cmd.redo()
      }
    }
  }

  private def filterList(source: Source[?]): List[Filter[?]] = {
    source.getFilters().asInstanceOf[List[Filter[?]]]
  }

  // ──────────────── 批量命令（可合并） ────────────────

  /** 批量移动片段命令。合并时：同 segment 保留旧起点、更新终点；新 segment 直接追加。 */
  final class MoveSegsCommand(entries0: List[MoveSegsCommand.MoveEntry]) extends MergeableCommand {
    private final val entries: List[MoveSegsCommand.MoveEntry] = new ArrayList[MoveSegsCommand.MoveEntry](entries0)

    override def undo(): Unit = {
      var i = entries.size() - 1
      while (i >= 0) {
        val e = entries.get(i)
        e.toTrack.remove(e.segment)
        e.fromTrack.`override`(e.segment, e.oldRange)
        e.segment.offsetOrigin(e.oldRange.lowerEndpoint() - e.newRange.lowerEndpoint())
        i -= 1
      }
    }

    override def redo(): Unit = {
      for (e <- entries.asScala) {
        e.fromTrack.remove(e.segment)
        e.toTrack.`override`(e.segment, e.newRange)
        e.segment.offsetOrigin(e.newRange.lowerEndpoint() - e.oldRange.lowerEndpoint())
      }
    }

    override def merge(other: UndoableCommand): Boolean = {
      other match {
        case o: MoveSegsCommand =>
          for (ne <- o.entries.asScala) {
            var found = false
            var i = 0
            while (i < entries.size() && !found) {
              val e = entries.get(i)
              if (e.segment eq ne.segment) {
                entries.set(i, MoveSegsCommand.MoveEntry(e.fromTrack, ne.toTrack, e.segment,
                  e.oldRange, ne.newRange))
                found = true
              }
              i += 1
            }
            if (!found) entries.add(ne)
          }
          true
        case _ =>
          false
      }
    }
  }

  object MoveSegsCommand {
    case class MoveEntry(fromTrack: Track, toTrack: Track, segment: Segment,
                         oldRange: Range[java.lang.Long], newRange: Range[java.lang.Long])
  }

  /** 批量调整片段区间命令。合并时：同 segment 保留旧区间、更新新区间；新 segment 直接追加。 */
  final class ResizeSegsCommand(entries0: List[ResizeSegsCommand.ResizeEntry]) extends MergeableCommand {
    private final val entries: List[ResizeSegsCommand.ResizeEntry] = new ArrayList[ResizeSegsCommand.ResizeEntry](entries0)

    override def undo(): Unit = {
      var i = entries.size() - 1
      while (i >= 0) {
        val e = entries.get(i)
        e.track.remove(e.segment)
        e.track.`override`(e.segment, e.oldRange)
        i -= 1
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
            var found = false
            var i = 0
            while (i < entries.size() && !found) {
              val e = entries.get(i)
              if (e.segment eq ne.segment) {
                entries.set(i, ResizeSegsCommand.ResizeEntry(e.track, e.segment,
                  e.oldRange, ne.newRange))
                found = true
              }
              i += 1
            }
            if (!found) entries.add(ne)
          }
          true
        case _ =>
          false
      }
    }
  }

  object ResizeSegsCommand {
    case class ResizeEntry(track: Track, segment: Segment,
                           oldRange: Range[java.lang.Long], newRange: Range[java.lang.Long])
  }

  /** 批量删除片段命令。合并时直接追加新条目（去重）。 */
  final class RemoveSegsCommand(entries0: List[RemoveSegsCommand.RemoveEntry]) extends MergeableCommand {
    private final val entries: List[RemoveSegsCommand.RemoveEntry] = new ArrayList[RemoveSegsCommand.RemoveEntry](entries0)

    override def undo(): Unit = {
      var i = entries.size() - 1
      while (i >= 0) {
        val e = entries.get(i)
        e.track.`override`(e.segment, e.range)
        if (e.group != null) e.group.add(e.segment)
        i -= 1
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
            var exists = false
            val it = entries.iterator()
            while (it.hasNext && !exists) {
              val e = it.next()
              if (e.segment eq ne.segment) exists = true
            }
            if (!exists) entries.add(ne)
          }
          true
        case _ =>
          false
      }
    }
  }

  object RemoveSegsCommand {
    case class RemoveEntry(track: Track, segment: Segment, range: Range[java.lang.Long],
                           group: SegmentGroup)
  }

  def postRefresh(source: Source[?]): Unit = {
    val seg: Segment = source.getSegment()
    if (seg != null) {
      val track: Track = seg.getTrack()
      if (track != null) {
        val timeline: Timeline = track.getTimeline()
        timeline.project.projEventBus.post(new SegmentSelectedEvent(seg, track, 1))
        timeline.project.projEventBus.post(RefreshRequestEvent.INSTANCE)
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
      val `def`: NumFrame = port.getDefaultData().asInstanceOf[NumFrame]
      if (`def` != null) `def`.setVal(v)
      else port.getData().asInstanceOf[NumFrame].setVal(v)
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
