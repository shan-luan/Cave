package com.lomekwi.cave.app.shortcut

trait ShortcutAction {
  def displayName(): String = {
    toString()
  }

  def defaultKeys(): Array[Int] = {
    new Array[Int](0)
  }
}
