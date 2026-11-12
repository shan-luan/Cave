package com.lomekwi.cave.resource.decoder

import com.lomekwi.cave.timeline.Timespan
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*


import com.badlogic.gdx.Gdx
import com.lomekwi.cave.pipeline.image.ImgFrame
import com.lomekwi.cave.resource.media.VdoRes
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.Frame

import java.nio.ByteBuffer
import scala.compiletime.uninitialized

class VdoDecRes(segment: VdoRes) extends DecRes[ImgFrame](segment) {
  private var bufferedPixels: ByteBuffer = uninitialized
  private var unpackRowLength: Int = 0

  protected def setPixelFormat(pixelFormat: Int): Unit = {
    grabber.setPixelFormat(pixelFormat)
  }

  override def grab(): Frame = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.grabImage()
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

  def getLengthInVideoFrames: Int = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getLengthInVideoFrames
  }

  override def codecName: String = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getVideoCodecName
  }

  override def codec: Int = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getVideoCodec
  }

  override def lengthPerFrame: Timespan = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    Timespan(Math.round(Timespan.SECOND.value / grabber.getFrameRate))
  }

  override protected def configure(): Unit = {
    grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA)
    grabber.setAudioChannels(0)
  }

  override def close(): Unit = {
    super.close()
  }

  override def sync(time: Timespan): Unit = {
    if (!initialized) {
      start()
    }

    val validTime = toValidTime(time)
    val diff = validTime - getLastFrameTime

    if (diff < Timespan.Zero || diff > lengthPerFrame * 2) {
      seek(validTime)
      bufferedPixels = null
    }
    Gdx.app.debug(("视频解码".in(Zh) | "Video Decode".in(En)).str(), hashCode().toString + ("同步到".in(Zh) | "Synchronized to ".in(En)).str() + (validTime / Timespan.SECOND).toString + ("秒".in(Zh) | "s".in(En)).str())
  }

  /**
   * 解码指定时间戳的视频帧，并将像素数据更新到提供的帧对象中。
   *
   * @param time  目标时间
   * @param frame 要更新的帧对象
   */
  override def get(time: Timespan, frame: ImgFrame): Unit = {
    if (!initialized) {
      start()
    }

    if (isTimeLegal(time)) {
      val nextFrameTime = getTimestamp + lengthPerFrame

      if (!((time < nextFrameTime) && bufferedPixels != null)) {
        var output: Frame = null
        var retryCount = 0
        val maxRetries = 10
        while (output == null && retryCount < maxRetries) {
          output = grab()
          retryCount += 1
        }
        if (output != null) {
          bufferedPixels = output.image(0).asInstanceOf[ByteBuffer]
          if (output.imageStride > 0 && output.imageChannels > 0) {
            unpackRowLength = output.imageStride / output.imageChannels
          }
        }
      }

      frame.setPixels(bufferedPixels)
      frame.unpackRowLength = unpackRowLength
    }
  }

  override def seek(time: Timespan): Unit = {
    grabber.setVideoTimestamp(time.value)
  }
}
