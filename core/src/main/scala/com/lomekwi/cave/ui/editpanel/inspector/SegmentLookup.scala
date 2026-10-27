package com.lomekwi.cave.ui.editpanel.inspector

import com.lomekwi.cave.app.App
import com.lomekwi.cave.project.Id
import com.lomekwi.cave.timeline.Segment

private[inspector] object SegmentLookup {
  def resolve(id: Id): Segment = {
    val context = App.root.getFrontendContext
    if (context == null) null else context.timeline.segment(id).orNull
  }
}
