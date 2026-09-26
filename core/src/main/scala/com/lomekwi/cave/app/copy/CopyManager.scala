package com.lomekwi.cave.app.copy

import com.google.common.eventbus.Subscribe
import com.lomekwi.cave.app.selection.SegmentSetSelectedEvent

import scala.compiletime.uninitialized

class CopyManager {
  private var clipboard: Copyable = uninitialized
  private var latestCopyable: Copyable = uninitialized

  /** 复制以"当前选中集"为单位，单选也是只有一项的集合。 */
  @Subscribe
  def onSelection(e: SegmentSetSelectedEvent): Unit = {
    latestCopyable = e.set
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

  def getClipboard: Copyable = {
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
