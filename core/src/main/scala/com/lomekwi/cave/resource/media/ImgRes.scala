package com.lomekwi.cave.resource.media

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.image.ImgFrame
import com.lomekwi.cave.resource.decoder.DecRes
import com.lomekwi.cave.resource.decoder.ImgDecRes

import java.io.ObjectInputStream
import java.nio.ByteBuffer

import scala.util.Using

import com.lomekwi.cave.util.Units.SECOND

@SerialVersionUID(1L)
class ImgRes(path: String) extends MedRes(path) with Previewable with Showable {
  private var width: Int = scala.compiletime.uninitialized
  private var height: Int = scala.compiletime.uninitialized
  @transient private var cachedPixels: ByteBuffer = scala.compiletime.uninitialized
  @transient private var unpackRowLength: Int = scala.compiletime.uninitialized
  @transient @volatile private var decoded: Boolean = scala.compiletime.uninitialized
  @transient private var texture: Texture = null


  override protected def newDecoder(): ImgDecRes = {
    new ImgDecRes(this)
  }

  override protected def generateMetadata(metadataDecRes: DecRes[?]): Unit = {
    val idr = metadataDecRes.asInstanceOf[ImgDecRes]
    width = idr.getWidth()
    height = idr.getHeight()
    val tmp = new ImgFrame(null)
    try {
      idr.get(0, tmp)
      cachedPixels = idr.getCachedPixels()
      unpackRowLength = idr.getUnpackRowLength()
      decoded = true
    } catch {
      case e: Exception =>
        // 首次 get() 时才惰性解码
    }
  }

  def getWidth(): Int = {
    width
  }

  def getHeight(): Int = {
    height
  }

  def getFrameLength(): Long = {
    SECOND / 30
  }

  override def get(trackIndex: Int, time: Long, frame: Frame): Unit = {
    if (!decoded) {
      Using.resource(new ImgDecRes(this)) { dec =>
        dec.start()
        val tmp = new ImgFrame(null)
        dec.get(0, tmp)
        cachedPixels = dec.getCachedPixels()
        unpackRowLength = dec.getUnpackRowLength()
      }
      decoded = true
    }
    val imgFrame = frame.asInstanceOf[ImgFrame]
    imgFrame.setPixels(cachedPixels)
    imgFrame.setUnpackRowLength(unpackRowLength)
  }

  override def sync(trackIndex: Int, time: Long): Unit = {
  }

  def getTexture(): Texture = {
    if (texture != null) {
      return texture
    }
    if (cachedPixels == null) {
      return null
    }
    texture = new Texture(width, height, Pixmap.Format.RGBA8888)
    texture.bind()
    cachedPixels.rewind()
    Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, unpackRowLength)
    Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, width, height,
      GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, cachedPixels)
    Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, 0)
    texture
  }

  override def getPreview(time: Long): Texture = {
    getTexture()
  }

  override def getPreview(): Texture = {
    getTexture()
  }

  override def getPreviewInterval(): Long = {
    SECOND
  }

  override def close(): Unit = {
    super.close()
    cachedPixels = null
    decoded = false
    if (texture != null) {
      texture.dispose()
      texture = null
    }
  }

  private def readObject(ois: ObjectInputStream): Unit = {
    ois.defaultReadObject()
    decoded = false
    cachedPixels = null
  }
}
