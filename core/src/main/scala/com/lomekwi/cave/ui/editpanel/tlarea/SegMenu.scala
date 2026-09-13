package com.lomekwi.cave.ui.editpanel.tlarea

import com.kotcrab.vis.ui.widget.{MenuItem, PopupMenu}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.listeners.ChangeListenerX

class SegMenu private[tlarea] (private val tlGroup: TlGroup) extends PopupMenu {
  private var segActor: SegActor = null
  private var time: Long = 0L

  addItem(new MenuItem("复制", new ChangeListenerX(() => {
    if (tlGroup.selectedSegments.contains(segActor.getSegment())) {
      App.copyManager.copy()
    }
  })))
  addItem(new MenuItem("删除", new ChangeListenerX(() => tlGroup.removeSeg(segActor))))
  addItem(new MenuItem("分割", new ChangeListenerX(() => tlGroup.split(segActor, time))))

  def setContext(segActor: SegActor, time: Long): Unit = {
    this.segActor = segActor
    this.time = time
  }
}
