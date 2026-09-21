package com.lomekwi.cave.app.selection

/**
 * 时间线选中集发生变化。选中不是模型属性，事件只在界面层流转。
 */
case class SourceSetSelectedEvent(set: SourceSet, selectedCount: Int)
