package com.lomekwi.cave.ui.editpanel.tlarea

import com.kotcrab.vis.ui.widget.{MenuItem, PopupMenu}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.listeners.ChangeListenerX
import scala.compiletime.uninitialized

class TlSrcMenu private[tlarea] (private val timelineView: TimelineView) extends PopupMenu {
  private var srcActor: TlSrcActor = uninitialized
  private var time: Long = 0L

  addItem(new MenuItem("复制", new ChangeListenerX(() => {
    if (timelineView.selectedSources.contains(srcActor.getSource)) {
      App.copyManager.copy()
    }
  })))
  addItem(new MenuItem("删除", new ChangeListenerX(() => timelineView.removeSource(srcActor))))
  addItem(new MenuItem("分割", new ChangeListenerX(() => timelineView.split(srcActor, time))))

  def setContext(srcActor: TlSrcActor, time: Long): Unit = {
    this.srcActor = srcActor
    this.time = time
  }
}
