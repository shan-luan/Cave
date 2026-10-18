package com.lomekwi.cave.ui.editpanel.tlarea

import com.kotcrab.vis.ui.widget.{MenuItem, PopupMenu}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.listeners.ChangeListenerX
import com.lomekwi.cave.util.legacy_i18n.I18N.i18n
import scala.compiletime.uninitialized

class TlSegmentMenu private[tlarea] (private val timelineView: TimelineView) extends PopupMenu {
  private var segmentActor: TlSegmentActor = uninitialized
  private var time: Long = 0L

  addItem(new MenuItem(i18n("复制"), new ChangeListenerX(() => {
    if (timelineView.selectedSegments.contains(segmentActor.segment)) {
      App.copyManager.copy()
    }
  })))
  addItem(new MenuItem(i18n("删除"), new ChangeListenerX(() => timelineView.removeSegment(segmentActor))))
  addItem(new MenuItem(i18n("分割"), new ChangeListenerX(() => timelineView.split(segmentActor, time))))

  def setContext(segmentActor: TlSegmentActor, time: Long): Unit = {
    this.segmentActor = segmentActor
    this.time = time
  }
}
