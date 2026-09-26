package com.lomekwi.cave.ui.editpanel.tlarea

import com.kotcrab.vis.ui.widget.{MenuItem, PopupMenu}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.listeners.ChangeListenerX
import scala.compiletime.uninitialized

class TlSegmentMenu private[tlarea] (private val timelineView: TimelineView) extends PopupMenu {
  private var segmentActor: TlSegmentActor = uninitialized
  private var time: Long = 0L

  addItem(new MenuItem("复制", new ChangeListenerX(() => {
    if (timelineView.selectedSegments.contains(segmentActor.getSegment)) {
      App.copyManager.copy()
    }
  })))
  addItem(new MenuItem("删除", new ChangeListenerX(() => timelineView.removeSegment(segmentActor))))
  addItem(new MenuItem("分割", new ChangeListenerX(() => timelineView.split(segmentActor, time))))

  def setContext(segmentActor: TlSegmentActor, time: Long): Unit = {
    this.segmentActor = segmentActor
    this.time = time
  }
}
