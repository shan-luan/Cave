package com.lomekwi.cave.app.shortcut

trait ShortcutAction {
  def displayName(): String = {
    toString
  }

  def defaultKeys(): Array[Int] = {
    Array.empty[Int]
  }
}
