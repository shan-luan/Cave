package com.lomekwi.cave.resource.decoder

import com.lomekwi.cave.util.Units.SECOND
import com.lomekwi.cave.util.i18n.I18N.i18n


import com.badlogic.gdx.Gdx
import com.lomekwi.cave.pipeline.image.ImgFrame
import com.lomekwi.cave.resource.media.VdoRes
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.Frame

import java.nio.ByteBuffer

class VdoDecRes(source: VdoRes) extends DecRes[ImgFrame](source) {
  private var bufferedPixels: ByteBuffer = null
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

  def getWidth(): Int = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getImageWidth
  }

  def getHeight(): Int = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getImageHeight
  }

  def getLengthInVideoFrames(): Int = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getLengthInVideoFrames
  }

  override def getCodecName(): String = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getVideoCodecName
  }

  override def getCodec(): Int = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getVideoCodec
  }

  override def getLengthPerFrame(): Long = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    Math.round(SECOND / grabber.getFrameRate)
  }

  override protected def configure(): Unit = {
    grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA)
    grabber.setAudioChannels(0)
  }

  def setBufferedPixels(pixels: ByteBuffer): Unit = {
    bufferedPixels = pixels
  }

  def getBufferedPixels(): ByteBuffer = {
    bufferedPixels
  }

  override def close(): Unit = {
    super.close()
  }

  override def sync(time: Long): Unit = {
    if (!initialized) {
      start()
    }

    val validTime = toValidTime(time)
    val diff = validTime - getLastFrameTime()

    if (diff < 0 || diff > 2 * getLengthPerFrame()) {
      seek(validTime)
      bufferedPixels = null
    }
    Gdx.app.debug(i18n("视频解码"), hashCode().toString + "同步到" + (validTime / SECOND).toString + i18n("秒"))
  }

  /**
   * 解码指定时间戳的视频帧，并将像素数据更新到提供的帧对象中。
   * 内部会根据时间戳判断是否使用缓存、跳转或抓取新帧。
   *
   * @param time  目标时间
   * @param frame 要更新的帧对象
   * @throws Exception 解码过程中的异常
   */
  override def get(time: Long, frame: ImgFrame): Unit = {
    if (!initialized) {
      start()
    }

    if (!isTimeLegal(time)) {
      return
    }
    val nextFrameTime = getTimestamp() + getLengthPerFrame()

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

    // 目标时间在下一帧之前，且缓存有效，直接返回缓存
    frame.setPixels(bufferedPixels)
    frame.setUnpackRowLength(unpackRowLength)
  }

  override def seek(time: Long): Unit = {
    grabber.setVideoTimestamp(time)
  }
}
