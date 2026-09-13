package com.lomekwi.cave.timeline

import com.lomekwi.cave.app.selection.SelectableSelectedEvent

case class SegmentSetSelectedEvent(set: SegmentSet, selectedCount: Int)
    extends SelectableSelectedEvent[SegmentSet] {
  override def selectable(): SegmentSet = set
}
