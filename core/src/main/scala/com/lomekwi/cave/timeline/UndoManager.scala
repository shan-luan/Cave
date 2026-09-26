package com.lomekwi.cave.timeline

import com.lomekwi.cave.app.selection.SegmentNodeChangedEvent
import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Segment
import com.lomekwi.cave.pipeline.image.TransNode
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.project.ProjectDirtyChangedEvent
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent


import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import java.util

class UndoManager(@transient private val project: Project) {
  private final val undoStack: mutable.ArrayDeque[UndoManager.UndoableCommand] = mutable.ArrayDeque.empty[UndoManager.UndoableCommand]
  private final val redoStack: mutable.ArrayDeque[UndoManager.UndoableCommand] = mutable.ArrayDeque.empty[UndoManager.UndoableCommand]

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
    val top = if (undoStack.isEmpty) null else undoStack.head
    val merged = (command, top) match {
      case (_: UndoManager.MergeableCommand, topMc: UndoManager.MergeableCommand) =>
        topMc.getClass == command.getClass && topMc.merge(command)
      case _ => false
    }
    if (!merged) {
      undoStack.prepend(command)
    }
    redoStack.clear()
    if (undoStack.size > UndoManager.MAX_UNDO) {
      undoStack.removeLast()
    }
  }

  def undo(): Unit = {
    if (!undoStack.isEmpty) {
      val wasDirty = project.isDirty
      project.currentVersion = project.currentVersion - 1
      val command = undoStack.removeHead()
      command.undo()
      redoStack.prepend(command)
      if (wasDirty != project.isDirty) {
        project.projEventBus.post(ProjectDirtyChangedEvent)
      }
    }
  }

  def redo(): Unit = {
    if (!redoStack.isEmpty) {
      val wasDirty = project.isDirty
      project.currentVersion = project.currentVersion + 1
      val command = redoStack.removeHead()
      command.redo()
      undoStack.prepend(command)
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
   * 可合并命令，同类型命令可合并为一个，避免栈中存在连续的同类型记录。
   */
  trait MergeableCommand extends UndoableCommand {
    /**
     * 将 other 合并到当前命令中。合并后，undo() 应能撤销两者的效果，
     * redo() 应能重做合并后的效果。
     * @return true 表示合并成功
     */
    def merge(other: UndoableCommand): Boolean
  }

  /**
   * 一条轨道的一次版本替换，撤销即换回 before，重做即换成 after。
   * 轨道不可变，两端版本即完整描述一次改动，且新旧版本共享绝大部分节点，
   * 因此快照本身很轻。
   */
  case class TrackEdit(index: Int, before: Track, after: Track)

  /** 把同轨道的多条替换折叠成「最早的 before」与「最新的 after」，用于成批且原子地恢复。 */
  private def foldBefore(edits: Iterable[TrackEdit]): Seq[(Int, Track)] = {
    val m = mutable.LinkedHashMap.empty[Int, Track]
    for (e <- edits) if (!m.contains(e.index)) m.put(e.index, e.before)
    m.toSeq
  }

  private def foldAfter(edits: Iterable[TrackEdit]): Seq[(Int, Track)] = {
    val m = mutable.LinkedHashMap.empty[Int, Track]
    for (e <- edits) m.put(e.index, e.after)
    m.toSeq
  }

  case class AddSegmentCommand(timeline: Timeline, index: Int, before: Track, after: Track) extends UndoableCommand {
    override def undo(): Unit = timeline.setTrack(index, before)

    override def redo(): Unit = timeline.setTrack(index, after)
  }

  case class RemoveSegmentCommand(timeline: Timeline, index: Int, before: Track, after: Track,
                                 segment: Segment[?], group: SegmentGroup) extends UndoableCommand {
    override def undo(): Unit = {
      timeline.setTrack(index, before)
      if (group != null) group.add(segment)
    }

    override def redo(): Unit = {
      timeline.setTrack(index, after)
      if (group != null) group.remove(segment)
    }
  }

  case class SplitSegmentCommand(timeline: Timeline, index: Int, before: Track, after: Track) extends UndoableCommand {
    override def undo(): Unit = timeline.setTrack(index, before)

    override def redo(): Unit = timeline.setTrack(index, after)
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

  private def filterList(segment: Segment[?]): util.List[Filter[?]] = {
    segment.getFilters.asInstanceOf[util.List[Filter[?]]]
  }

  /**
   * 批量轨道替换命令，一次操作在若干轨道上留下的版本变化。
   * 撤销时把涉及的轨道整体换回旧版本，因此不必逐片段回放。
   */
  private[timeline] abstract class BatchTrackCommand(protected val timeline: Timeline,
                                                     protected val edits: util.List[TrackEdit]) extends MergeableCommand {
    override def undo(): Unit = timeline.setTracks(UndoManager.foldBefore(edits.asScala))

    override def redo(): Unit = timeline.setTracks(UndoManager.foldAfter(edits.asScala))

    /** 合并时同一轨道保留最早的 before、取最新的 after。 */
    protected def mergeEdits(other: util.List[TrackEdit]): Unit = {
      for (ne <- other.asScala) {
        val idx = edits.asScala.indexWhere(e => e.index == ne.index)
        if (idx < 0) {
          edits.add(ne)
        } else {
          edits.set(idx, TrackEdit(ne.index, edits.get(idx).before, ne.after))
        }
      }
    }
  }

  final class MoveSegmentsCommand(timeline0: Timeline, entries0: util.List[TrackEdit])
    extends BatchTrackCommand(timeline0, new util.ArrayList[TrackEdit](entries0)) {

    override def merge(other: UndoableCommand): Boolean = other match {
      case o: MoveSegmentsCommand =>
        mergeEdits(o.edits)
        true
      case _ =>
        false
    }
  }

  final class ResizeSegmentsCommand(timeline0: Timeline, entries0: util.List[TrackEdit])
    extends BatchTrackCommand(timeline0, new util.ArrayList[TrackEdit](entries0)) {

    override def merge(other: UndoableCommand): Boolean = other match {
      case o: ResizeSegmentsCommand =>
        mergeEdits(o.edits)
        true
      case _ =>
        false
    }
  }

  final class RemoveSegmentsCommand(private val timeline: Timeline, entries0: util.List[RemoveSegmentsCommand.RemoveEntry]) extends MergeableCommand {
    private final val entries: util.List[RemoveSegmentsCommand.RemoveEntry] = new util.ArrayList[RemoveSegmentsCommand.RemoveEntry](entries0)

    override def undo(): Unit = {
      timeline.setTracks(UndoManager.foldBefore(entries.asScala.map(_.edit)))
      for (e <- entries.asScala.reverseIterator) {
        if (e.group != null) e.group.add(e.segment)
      }
    }

    override def redo(): Unit = {
      timeline.setTracks(UndoManager.foldAfter(entries.asScala.map(_.edit)))
      for (e <- entries.asScala) {
        if (e.group != null) e.group.remove(e.segment)
      }
    }

    override def merge(other: UndoableCommand): Boolean = {
      other match {
        case o: RemoveSegmentsCommand =>
          for (ne <- o.entries.asScala) {
            if (!entries.asScala.exists(e => e.segment eq ne.segment)) entries.add(ne)
          }
          true
        case _ =>
          false
      }
    }
  }

  object RemoveSegmentsCommand {
    case class RemoveEntry(edit: TrackEdit, segment: Segment[?], group: SegmentGroup)
  }

  /** 节点图被改动后通知界面重建，只在片段确实位于时间轴上时通知。 */
  private def postRefresh(project: Project, segment: Segment[?]): Unit = {
    if (segment != null && project.timeline.findTrackOf(segment) != null) {
      project.projEventBus.post(SegmentNodeChangedEvent(segment))
      project.projEventBus.post(RefreshRequestEvent)
    }
  }

  case class AddFilterCommand(project: Project, segment: Segment[?], filter: Filter[?]) extends UndoableCommand {
    override def undo(): Unit = {
      filterList(segment).remove(filter)
      postRefresh(project, segment)
    }

    override def redo(): Unit = {
      filterList(segment).add(filter)
      postRefresh(project, segment)
    }
  }

  case class RemoveFilterCommand(project: Project, segment: Segment[?], filter: Filter[?], index: Int) extends UndoableCommand {
    override def undo(): Unit = {
      filterList(segment).add(index, filter)
      postRefresh(project, segment)
    }

    override def redo(): Unit = {
      filterList(segment).remove(filter)
      postRefresh(project, segment)
    }
  }

  case class ReorderFilterCommand(project: Project, segment: Segment[?], filter: Filter[?], oldIndex: Int, newIndex: Int) extends UndoableCommand {
    override def undo(): Unit = {
      filterList(segment).remove(filter)
      filterList(segment).add(oldIndex, filter)
      postRefresh(project, segment)
    }

    override def redo(): Unit = {
      filterList(segment).remove(filter)
      filterList(segment).add(newIndex, filter)
      postRefresh(project, segment)
    }
  }

  case class FpPortValueCommand(project: Project, port: Node.InPort[?], segment: Segment[?], oldValue: Double, newValue: Double) extends UndoableCommand {
    override def undo(): Unit = {
      setValue(oldValue)
    }

    override def redo(): Unit = {
      setValue(newValue)
    }

    private def setValue(v: Double): Unit = {
      port.asInstanceOf[Node.InPort[Double]].setDefaultData(v)
      postRefresh(project, segment)
    }
  }

  case class TransNodeState(dx: Float, dy: Float, scaleX: Float, scaleY: Float,
                            dRotation: Float,
                            flipX: Boolean, flipY: Boolean)

  case class TransformNodeCommand(project: Project, segment: Segment[?], node: TransNode, oldState: TransNodeState, newState: TransNodeState) extends UndoableCommand {
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
      postRefresh(project, segment)
    }
  }
}
