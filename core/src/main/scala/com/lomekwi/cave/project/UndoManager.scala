package com.lomekwi.cave.project

import scala.collection.mutable

final class UndoManager(@transient private val context: ProjectSession) {
  // FIXME: 从撤销/重做栈淘汰的 Project 快照中的 Resource 尚未 close，需在清栈和会话关闭时回收解码器池
  private val undoStack: mutable.ArrayDeque[Project] = mutable.ArrayDeque.empty
  private val redoStack: mutable.ArrayDeque[Project] = mutable.ArrayDeque.empty

  def push(project: Project): Unit = {
    undoStack.prepend(project)
    redoStack.clear()
    if (undoStack.size > UndoManager.MAX_UNDO) {
      undoStack.removeLast()
    }
  }

  def undo(): Unit = {
    if (undoStack.nonEmpty) {
      redoStack.prepend(context.project)
      context.applyProject(undoStack.removeHead())
      context.bumpVersion(-1)
    }
  }

  def redo(): Unit = {
    if (redoStack.nonEmpty) {
      undoStack.prepend(context.project)
      context.applyProject(redoStack.removeHead())
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
}
