package com.lomekwi.cave.resource.decoder

import com.lomekwi.cave.pipeline.image.ImgFrame
import com.lomekwi.cave.resource.media.ImgRes
import org.bytedeco.javacv.Frame

import java.nio.ByteBuffer

import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import scala.compiletime.uninitialized

class ImgDecRes(segment: ImgRes) extends DecRes[ImgFrame](segment) {
  private var cachedPixels: ByteBuffer = uninitialized
  private var unpackRowLength: Int = 0

  override def grab(): Frame = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.grabImage()
  }

  override protected def configure(): Unit = {
    grabber.setPixelFormat(AV_PIX_FMT_RGBA)
    grabber.setAudioChannels(0)
  }

  def getWidth: Int = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getImageWidth
  }

  def getHeight: Int = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getImageHeight
  }

  override def getLengthPerFrame: Long = {
    0
  }

  override def sync(time: Long): Unit = {
    if (!initialized) {
      start()
    }
  }

  override def get(time: Long, frame: ImgFrame): Unit = {
    if (!initialized) {
      start()
    }
    if (cachedPixels != null) {
      frame.setPixels(cachedPixels)
    } else {
      val grabbed = grab()
      if (grabbed != null && grabbed.image != null && grabbed.image(0) != null) {
        if (grabbed.imageStride > 0 && grabbed.imageChannels > 0) {
          unpackRowLength = grabbed.imageStride / grabbed.imageChannels
        }
        val src = grabbed.image(0).asInstanceOf[ByteBuffer]
        src.rewind()
        val copy = ByteBuffer.allocateDirect(src.limit())
        copy.put(src)
        copy.flip()
        cachedPixels = copy
      }
      frame.setPixels(cachedPixels)
    }
  }

  override def seek(time: Long): Unit = {
  }

  override def getCodecName: String = {
    grabber.getVideoCodecName
  }

  override def getCodec: Int = {
    grabber.getVideoCodec
  }

  def getCachedPixels: ByteBuffer = {
    cachedPixels
  }

  def getUnpackRowLength: Int = {
    unpackRowLength
  }
}
