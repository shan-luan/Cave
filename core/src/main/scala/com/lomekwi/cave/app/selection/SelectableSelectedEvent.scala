package com.lomekwi.cave.app.selection

trait SelectableSelectedEvent[T <: Selectable] {
  def selectable(): T
  def selectedCount: Int
}
