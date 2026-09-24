package com.lomekwi.cave.timeline

import com.lomekwi.cave.app.selection.SourceNodeChangedEvent
import com.lomekwi.cave.pipeline.Filter
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Source
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

  case class AddSegCommand(timeline: Timeline, index: Int, before: Track, after: Track) extends UndoableCommand {
    override def undo(): Unit = timeline.setTrack(index, before)

    override def redo(): Unit = timeline.setTrack(index, after)
  }

  case class RemoveSegCommand(timeline: Timeline, index: Int, before: Track, after: Track,
                              source: Source[?], group: SourceGroup) extends UndoableCommand {
    override def undo(): Unit = {
      timeline.setTrack(index, before)
      if (group != null) group.add(source)
    }

    override def redo(): Unit = {
      timeline.setTrack(index, after)
      if (group != null) group.remove(source)
    }
  }

  case class SplitSegCommand(timeline: Timeline, index: Int, before: Track, after: Track) extends UndoableCommand {
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

  private def filterList(source: Source[?]): util.List[Filter[?]] = {
    source.getFilters.asInstanceOf[util.List[Filter[?]]]
  }

  /**
   * 批量轨道替换命令，一次操作在若干轨道上留下的版本变化。
   * 撤销时把涉及的轨道整体换回旧版本，因此不必逐源回放。
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

  final class MoveSegsCommand(timeline0: Timeline, entries0: util.List[TrackEdit])
    extends BatchTrackCommand(timeline0, new util.ArrayList[TrackEdit](entries0)) {

    override def merge(other: UndoableCommand): Boolean = other match {
      case o: MoveSegsCommand =>
        mergeEdits(o.edits)
        true
      case _ =>
        false
    }
  }

  final class ResizeSegsCommand(timeline0: Timeline, entries0: util.List[TrackEdit])
    extends BatchTrackCommand(timeline0, new util.ArrayList[TrackEdit](entries0)) {

    override def merge(other: UndoableCommand): Boolean = other match {
      case o: ResizeSegsCommand =>
        mergeEdits(o.edits)
        true
      case _ =>
        false
    }
  }

  final class RemoveSegsCommand(private val timeline: Timeline, entries0: util.List[RemoveSegsCommand.RemoveEntry]) extends MergeableCommand {
    private final val entries: util.List[RemoveSegsCommand.RemoveEntry] = new util.ArrayList[RemoveSegsCommand.RemoveEntry](entries0)

    override def undo(): Unit = {
      timeline.setTracks(UndoManager.foldBefore(entries.asScala.map(_.edit)))
      for (e <- entries.asScala.reverseIterator) {
        if (e.group != null) e.group.add(e.source)
      }
    }

    override def redo(): Unit = {
      timeline.setTracks(UndoManager.foldAfter(entries.asScala.map(_.edit)))
      for (e <- entries.asScala) {
        if (e.group != null) e.group.remove(e.source)
      }
    }

    override def merge(other: UndoableCommand): Boolean = {
      other match {
        case o: RemoveSegsCommand =>
          for (ne <- o.entries.asScala) {
            if (!entries.asScala.exists(e => e.source eq ne.source)) entries.add(ne)
          }
          true
        case _ =>
          false
      }
    }
  }

  object RemoveSegsCommand {
    case class RemoveEntry(edit: TrackEdit, source: Source[?], group: SourceGroup)
  }

  /** 节点图被改动后通知界面重建，只在源确实位于时间轴上时通知。 */
  private def postRefresh(project: Project, source: Source[?]): Unit = {
    if (source != null && project.timeline.findTrackOf(source) != null) {
      project.projEventBus.post(SourceNodeChangedEvent(source))
      project.projEventBus.post(RefreshRequestEvent)
    }
  }

  case class AddFilterCommand(project: Project, source: Source[?], filter: Filter[?]) extends UndoableCommand {
    override def undo(): Unit = {
      filterList(source).remove(filter)
      postRefresh(project, source)
    }

    override def redo(): Unit = {
      filterList(source).add(filter)
      postRefresh(project, source)
    }
  }

  case class RemoveFilterCommand(project: Project, source: Source[?], filter: Filter[?], index: Int) extends UndoableCommand {
    override def undo(): Unit = {
      filterList(source).add(index, filter)
      postRefresh(project, source)
    }

    override def redo(): Unit = {
      filterList(source).remove(filter)
      postRefresh(project, source)
    }
  }

  case class ReorderFilterCommand(project: Project, source: Source[?], filter: Filter[?], oldIndex: Int, newIndex: Int) extends UndoableCommand {
    override def undo(): Unit = {
      filterList(source).remove(filter)
      filterList(source).add(oldIndex, filter)
      postRefresh(project, source)
    }

    override def redo(): Unit = {
      filterList(source).remove(filter)
      filterList(source).add(newIndex, filter)
      postRefresh(project, source)
    }
  }

  case class FpPortValueCommand(project: Project, port: Node.InPort[?], source: Source[?], oldValue: Double, newValue: Double) extends UndoableCommand {
    override def undo(): Unit = {
      setValue(oldValue)
    }

    override def redo(): Unit = {
      setValue(newValue)
    }

    private def setValue(v: Double): Unit = {
      port.asInstanceOf[Node.InPort[Double]].setDefaultData(v)
      postRefresh(project, source)
    }
  }

  case class TransNodeState(dx: Float, dy: Float, scaleX: Float, scaleY: Float,
                            dRotation: Float,
                            flipX: Boolean, flipY: Boolean)

  case class TransformNodeCommand(project: Project, source: Source[?], node: TransNode, oldState: TransNodeState, newState: TransNodeState) extends UndoableCommand {
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
      postRefresh(project, source)
    }
  }
}
