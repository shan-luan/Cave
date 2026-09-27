package com.lomekwi.cave.pipeline.image

trait Transformable extends Renderable {
  def transform: Transform
  def transform_=(transform: Transform): Unit
  def baseWidth: Float
  def baseHeight: Float
  def reset(): Unit = {
    transform.reset(0, 0)
  }
}
