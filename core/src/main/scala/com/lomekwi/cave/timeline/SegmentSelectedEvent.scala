package com.lomekwi.cave.timeline

import com.lomekwi.cave.app.selection.SelectableSelectedEvent

case class SegmentSelectedEvent(segment: Segment, track: Track, selectedCount: Int)
    extends SelectableSelectedEvent[Segment] {
  override def selectable(): Segment = segment
}
