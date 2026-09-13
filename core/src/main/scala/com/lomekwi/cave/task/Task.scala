package com.lomekwi.cave.task

import com.lomekwi.cave.app.App

trait Task extends Runnable with AutoCloseable {
  /**
   * [0,1]
   */
  def getProgress(): Float
  def getName(): String = {
    toString()
  }
  def getPool(): TaskPool = {
    App.taskPool
  }
}
