package com.lomekwi.cave.timeline

import com.lomekwi.cave.app.selection.SourceNodeChangedEvent
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

  case class AddSegCommand(track: Track, source: Source[?], range: Interval, origin: Long) extends UndoableCommand {
    override def undo(): Unit = {
      track.remove(source)
    }

    override def redo(): Unit = {
      track.addOrThrow(source, range, origin)
    }
  }

  case class RemoveSegCommand(track: Track, source: Source[?], range: Interval, origin: Long, group: SourceGroup) extends UndoableCommand {
    override def undo(): Unit = {
      track.addOrThrow(source, range, origin)
      if (group != null) group.add(source)
    }

    override def redo(): Unit = {
      track.remove(source)
      if (group != null) group.remove(source)
    }
  }

  case class SplitSegCommand(track: Track, originalSeg: Source[?], originalRange: Interval, originalOrigin: Long,
                             newSeg: Source[?], newOrigin: Long, splitTime: Long) extends UndoableCommand {
    override def undo(): Unit = {
      track.remove(originalSeg)
      track.remove(newSeg)
      track.addOrThrow(originalSeg, originalRange, originalOrigin)
    }

    override def redo(): Unit = {
      track.remove(originalSeg)
      track.remove(newSeg)
      track.addOrThrow(originalSeg, Interval(originalRange.lo, splitTime), originalOrigin)
      track.addOrThrow(newSeg, Interval(splitTime, originalRange.hi), newOrigin)
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

  // 批量命令（可合并）

  /** 批量移动命令。合并时：同源保留旧区间与旧 origin、更新新区间与 origin；新源直接追加。 */
  final class MoveSegsCommand(entries0: util.List[MoveSegsCommand.MoveEntry]) extends MergeableCommand {
    private final val entries: util.List[MoveSegsCommand.MoveEntry] = new util.ArrayList[MoveSegsCommand.MoveEntry](entries0)

    override def undo(): Unit = {
      entries.asScala.reverseIterator.foreach { e =>
        e.toTrack.remove(e.source)
        e.fromTrack.addOrThrow(e.source, e.oldRange, e.oldOrigin)
      }
    }

    override def redo(): Unit = {
      for (e <- entries.asScala) {
        e.fromTrack.remove(e.source)
        e.toTrack.addOrThrow(e.source, e.newRange, e.newOrigin)
      }
    }

    override def merge(other: UndoableCommand): Boolean = {
      other match {
        case o: MoveSegsCommand =>
          for (ne <- o.entries.asScala) {
            val idx = entries.asScala.indexWhere(e => e.source eq ne.source)
            if (idx < 0) {
              entries.add(ne)
            } else {
              val e = entries.get(idx)
              entries.set(idx, MoveSegsCommand.MoveEntry(e.fromTrack, ne.toTrack, e.source,
                e.oldRange, ne.newRange, e.oldOrigin, ne.newOrigin))
            }
          }
          true
        case _ =>
          false
      }
    }
  }

  object MoveSegsCommand {
    case class MoveEntry(fromTrack: Track, toTrack: Track, source: Source[?],
                         oldRange: Interval, newRange: Interval,
                         oldOrigin: Long, newOrigin: Long)
  }

  /** 批量调整区间命令。区间变动不改 origin。合并时保留旧区间、更新新区间。 */
  final class ResizeSegsCommand(entries0: util.List[ResizeSegsCommand.ResizeEntry]) extends MergeableCommand {
    private final val entries: util.List[ResizeSegsCommand.ResizeEntry] = new util.ArrayList[ResizeSegsCommand.ResizeEntry](entries0)

    override def undo(): Unit = {
      entries.asScala.reverseIterator.foreach { e =>
        e.track.remove(e.source)
        e.track.addOrThrow(e.source, e.oldRange, e.origin)
      }
    }

    override def redo(): Unit = {
      for (e <- entries.asScala) {
        e.track.remove(e.source)
        e.track.addOrThrow(e.source, e.newRange, e.origin)
      }
    }

    override def merge(other: UndoableCommand): Boolean = {
      other match {
        case o: ResizeSegsCommand =>
          for (ne <- o.entries.asScala) {
            val idx = entries.asScala.indexWhere(e => e.source eq ne.source)
            if (idx < 0) {
              entries.add(ne)
            } else {
              val e = entries.get(idx)
              entries.set(idx, ResizeSegsCommand.ResizeEntry(e.track, e.source, e.origin,
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
    case class ResizeEntry(track: Track, source: Source[?], origin: Long,
                           oldRange: Interval, newRange: Interval)
  }

  /** 批量删除命令。合并时直接追加新条目（去重）。 */
  final class RemoveSegsCommand(entries0: util.List[RemoveSegsCommand.RemoveEntry]) extends MergeableCommand {
    private final val entries: util.List[RemoveSegsCommand.RemoveEntry] = new util.ArrayList[RemoveSegsCommand.RemoveEntry](entries0)

    override def undo(): Unit = {
      entries.asScala.reverseIterator.foreach { e =>
        e.track.addOrThrow(e.source, e.range, e.origin)
        if (e.group != null) e.group.add(e.source)
      }
    }

    override def redo(): Unit = {
      for (e <- entries.asScala) {
        e.track.remove(e.source)
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
    case class RemoveEntry(track: Track, source: Source[?], range: Interval, origin: Long,
                           group: SourceGroup)
  }

  /** 节点图被改动后通知界面重建：只在源确实位于时间轴上时通知。 */
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

  /** 数值输入端口默认值变更命令。 */
  case class NumPortValueCommand(project: Project, port: Node.InPort[?], source: Source[?], oldValue: Double, newValue: Double) extends UndoableCommand {
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
