package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.math.Matrix4

class Transform(x: Float, y: Float, rotation: Float) {
  private final val matrix: Matrix4 = new Matrix4()
  private var flipX: Boolean = false
  private var flipY: Boolean = false

  this.matrix.translate(x, y, 0)
  if (rotation != 0) {
    this.matrix.rotate(0, 0, 1, rotation)
  }

  def this() = {
    this(0, 0, 0)
  }

  def applyLocal(dx: Float, dy: Float, scaleX: Float, scaleY: Float, dRotation: Float,
                 flipX: Boolean, flipY: Boolean): Unit = {
    if (flipX) this.flipX = !this.flipX
    if (flipY) this.flipY = !this.flipY

    val local = new Matrix4()
    local.translate(dx, dy, 0)
    if (dRotation != 0) {
      local.rotate(0, 0, 1, dRotation)
    }
    local.scale(scaleX, scaleY, 1)

    matrix.mul(local)
  }

  def reset(x: Float, y: Float): Unit = {
    matrix.idt()
    matrix.translate(x, y, 0)
    flipX = false
    flipY = false
  }

  def getX: Float = {
    matrix.`val`(Matrix4.M03)
  }

  def getY: Float = {
    matrix.`val`(Matrix4.M13)
  }

  def getRotation: Float = {
    val a = matrix.`val`(Matrix4.M00)
    val b = matrix.`val`(Matrix4.M01)
    val c = matrix.`val`(Matrix4.M10)
    val d = matrix.`val`(Matrix4.M11)
    Math.toDegrees(Math.atan2(c - b, a + d)).toFloat
  }

  def getScaleX: Float = {
    val a = matrix.`val`(Matrix4.M00)
    val c = matrix.`val`(Matrix4.M10)
    Math.sqrt(a * a + c * c).toFloat
  }

  def getScaleY: Float = {
    val b = matrix.`val`(Matrix4.M01)
    val d = matrix.`val`(Matrix4.M11)
    Math.sqrt(b * b + d * d).toFloat
  }

  def getRotationRadians: Float = {
    val a = matrix.`val`(Matrix4.M00)
    val b = matrix.`val`(Matrix4.M01)
    val c = matrix.`val`(Matrix4.M10)
    val d = matrix.`val`(Matrix4.M11)
    Math.atan2(c - b, a + d).toFloat
  }

  def getMatrix: Matrix4 = {
    matrix
  }

  def isFlipX: Boolean = {
    flipX
  }

  def isFlipY: Boolean = {
    flipY
  }
}
