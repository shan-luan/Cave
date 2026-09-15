package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.previewarea.TransFrameActor

import java.nio.ByteBuffer
import scala.compiletime.uninitialized

class ImgFrame(track: Track, source: Source[?]) extends Frame(track, source) with Transformable with Renderable {
  private var transform: Transform = uninitialized
  private var pixels: ByteBuffer = uninitialized
  @volatile private var pixelsDirty: Boolean = false
  private var texture: Texture = uninitialized
  private var actor: TransFrameActor = uninitialized
  private var unpackRowLength: Int = 0

  def this(track: Track) = {
    this(track, null)
  }
  override def getTransform: Transform = {
    transform
  }
  override def setTransform(transform: Transform): Unit = {
    this.transform = transform
  }
  override def getBaseWidth: Float = {
    texture.getWidth.toFloat
  }
  override def getBaseHeight: Float = {
    texture.getHeight.toFloat
  }
  def getPixels: ByteBuffer = {
    pixels
  }

  def setPixels(pixels: ByteBuffer): Unit = {
    this.pixels = pixels
    pixelsDirty = true
  }
  def getTexture: Texture = {
    texture
  }
  def setTexture(texture: Texture): ImgFrame = {
    this.texture = texture
    this
  }

  def setActor(actor: TransFrameActor): ImgFrame = {
    this.actor = actor
    this
  }

  def getUnpackRowLength: Int = {
    unpackRowLength
  }

  def setUnpackRowLength(unpackRowLength: Int): Unit = {
    this.unpackRowLength = unpackRowLength
  }

  def upload(): Unit = {
    if (pixelsDirty) {
      pixelsDirty = false
      if (pixels != null) {
        Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, unpackRowLength)
        texture.bind()
        Gdx.gl.glTexSubImage2D(
          GL20.GL_TEXTURE_2D,
          0,
          0,
          0,
          texture.getWidth,
          texture.getHeight,
          GL20.GL_RGBA,
          GL20.GL_UNSIGNED_BYTE,
          pixels
        )
        Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, 0)
      }
    }
  }
  override def close(): Unit = {
    super.close()
    if (actor != null && actor.getParent == null) {
      Gdx.app.postRunnable(() => close())
    } else {
      texture.dispose()
    }
  }

  def getActor: TransFrameActor = {
    actor
  }

  override def render(batch: Batch): Unit = {
    upload()
    val t = getTransform
    val baseW = getBaseWidth
    val baseH = getBaseHeight
    val scaleX = if (t.isFlipX) -1f else 1f
    val scaleY = if (t.isFlipY) -1f else 1f
    val w = baseW * t.getScaleX
    val h = baseH * t.getScaleY
    batch.draw(getTexture, t.getX, t.getY, w / 2, h / 2, w, h, scaleX, scaleY, t.getRotation, 0, 0, baseW.toInt, baseH.toInt, false, false)
  }
}
