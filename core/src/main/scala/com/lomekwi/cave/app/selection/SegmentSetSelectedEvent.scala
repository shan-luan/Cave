package com.lomekwi.cave.app.selection

/**
 * 时间线选中集发生变化。
 */
case class SegmentSetSelectedEvent(set: SegmentSet, selectedCount: Int)
