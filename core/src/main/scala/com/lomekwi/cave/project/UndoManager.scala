package com.lomekwi.cave.project

import com.lomekwi.cave.timeline.Timeline

import scala.collection.mutable

final class UndoManager(@transient private val context: ProjectContext) {
  private final val undoStack: mutable.ArrayDeque[UndoManager.TimelineEdit] = mutable.ArrayDeque.empty
  private final val redoStack: mutable.ArrayDeque[UndoManager.TimelineEdit] = mutable.ArrayDeque.empty

  def push(edit: UndoManager.TimelineEdit): Unit = {
    undoStack.prepend(edit)
    redoStack.clear()
    if (undoStack.size > UndoManager.MAX_UNDO) {
      undoStack.removeLast()
    }
  }

  def undo(): Unit = {
    if (undoStack.nonEmpty) {
      val edit = undoStack.removeHead()
      context.applyTimeline(edit.before)
      redoStack.prepend(edit)
      context.bumpVersion(-1)
    }
  }

  def redo(): Unit = {
    if (redoStack.nonEmpty) {
      val edit = redoStack.removeHead()
      context.applyTimeline(edit.after)
      undoStack.prepend(edit)
      context.bumpVersion(1)
    }
  }

  def canUndo: Boolean = undoStack.nonEmpty

  def canRedo: Boolean = redoStack.nonEmpty

  def clear(): Unit = {
    undoStack.clear()
    redoStack.clear()
  }
}

object UndoManager {
  private final val MAX_UNDO = 100

  case class TimelineEdit(before: Timeline, after: Timeline)
}
