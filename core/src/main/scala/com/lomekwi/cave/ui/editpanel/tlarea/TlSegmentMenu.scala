package com.lomekwi.cave.ui.editpanel.tlarea

import com.kotcrab.vis.ui.widget.{MenuItem, PopupMenu}
import com.lomekwi.cave.ui.listeners.ChangeListenerX
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given
import scala.compiletime.uninitialized

class TlSegmentMenu private[tlarea] (private val timelineView: TimelineView) extends PopupMenu {
  private var segmentActor: TlSegmentActor = uninitialized
  private var time: Long = 0L

  addItem(new MenuItem(("删除".in(Zh) | "Delete".in(En)).str(), new ChangeListenerX(() => timelineView.removeSegment(segmentActor))))
  addItem(new MenuItem(("分割".in(Zh) | "Split".in(En)).str(), new ChangeListenerX(() => timelineView.split(segmentActor, time))))

  def setContext(segmentActor: TlSegmentActor, time: Long): Unit = {
    this.segmentActor = segmentActor
    this.time = time
  }
}
