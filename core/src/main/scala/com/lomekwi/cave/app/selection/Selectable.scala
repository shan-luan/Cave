package com.lomekwi.cave.app.selection

trait Selectable {
  def isSelected: Boolean
  def setSelected(selected: Boolean): Unit
}
