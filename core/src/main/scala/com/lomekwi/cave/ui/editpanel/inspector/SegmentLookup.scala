package com.lomekwi.cave.ui.editpanel.inspector

import com.lomekwi.cave.app.App
import com.lomekwi.cave.project.Id
import com.lomekwi.cave.timeline.Segment

private[inspector] object SegmentLookup {
  def resolve(id: Id): Segment = {
    val session = App.root.getFrontendSession
    if (session == null) null else session.timeline.segment(id).orNull
  }
}
