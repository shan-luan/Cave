package com.lomekwi.cave.pipeline.image

trait Transformable extends Renderable {
  def getTransform(): Transform
  def setTransform(transform: Transform): Unit
  def getBaseWidth(): Float
  def getBaseHeight(): Float
  def reset(): Unit = {
    val t = getTransform()
    t.reset(0, 0)
  }
}
