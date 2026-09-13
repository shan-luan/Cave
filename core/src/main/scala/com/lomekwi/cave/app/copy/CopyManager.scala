package com.lomekwi.cave.app.copy

import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.app.selection.SelectableSelectedEvent

class CopyManager {
  private var clipboard: Copyable = null
  private var latestCopyable: Copyable = null

  @Subscribe
  def onSelection(e: SelectableSelectedEvent[?]): Unit = {
    val sel = e.selectable()
    sel match {
      case c: Copyable => latestCopyable = c
      case _ =>
    }
  }

  @Subscribe
  def onCopyEvent(e: CopyEvent): Unit = {
    e.target match {
      case c: Copyable => clipboard = c.copy()
      case _ =>
    }
  }

  def copy(): Unit = {
    if (latestCopyable != null) {
      clipboard = latestCopyable.copy()
    }
  }

  def copy(copyable: Copyable): Unit = {
    clipboard = copyable
  }

  def getClipboard(): Copyable = {
    clipboard
  }

  def clearClipboard(): Unit = {
    clipboard = null
  }

  def refreshClipboard(): Unit = {
    if (clipboard != null) {
      clipboard = clipboard.copy()
    }
  }
}
