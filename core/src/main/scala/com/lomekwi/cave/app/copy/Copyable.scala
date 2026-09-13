package com.lomekwi.cave.app.copy

import com.lomekwi.cave.app.selection.Selectable

trait Copyable extends Selectable {
  def copy(): Copyable
}
