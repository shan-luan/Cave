package com.lomekwi.cave.resource.decoder

import com.lomekwi.cave.util.Units.SECOND
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLT
import com.lomekwi.cave.pipeline.audio.AudFrame
import com.lomekwi.cave.resource.media.AudRes
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame

import java.nio.FloatBuffer
import java.util
import scala.annotation.tailrec

class AudDecRes(segment: AudRes) extends DecRes[AudFrame](segment) {
  import AudDecRes.*

  private final val sampleBuf: Array[Float] = new Array[Float](FRAME_SIZE * 8)
  private final val output: Array[Float] = new Array[Float](FRAME_SIZE)
  private var bufLen: Int = 0

  override def grab(): Frame = {
    grabber.grabSamples()
  }

  override def seek(time: Long): Unit = {
    seek(time, 0)        // 底层即使传入合法时间也可能触发一次非法跳跃（如目标时间为负），这里用重试兜底。
  }

  @tailrec
  private def seek(time: Long, retry: Int): Unit = {
    bufLen = 0
    try {
      grabber.setAudioTimestamp(time)
    } catch {
      case e: FFmpegFrameGrabber.Exception =>
        if (retry >= 10) {
          throw e
        }
        seek(time + 1000, retry + 1)
    }
  }


  override def sync(time: Long): Unit = {
    if (!initialized) {
      start()
    }
    val validTime = toValidTime(time)
    seek(validTime)
  }

  override def get(time: Long, frame: AudFrame): Unit = {
    if (!initialized) {
      start()
    }

    if (!isTimeLegal(time)) {
      frame.samples = null
      return
    }

    var written = 0
    util.Arrays.fill(output, 0f)

    // 先使用缓冲区中遗留的采样点，没有则轻量同步到目标时间
    if (bufLen > 0) {
      val toCopy = Math.min(bufLen, FRAME_SIZE)
      System.arraycopy(sampleBuf, 0, output, 0, toCopy)
      written = toCopy
      if (toCopy < bufLen) {
        System.arraycopy(sampleBuf, toCopy, sampleBuf, 0, bufLen - toCopy)
        bufLen -= toCopy
      } else {
        bufLen = 0
      }
    } else {
      var i = 0
      var found = false
      while (i < 50 && !found) {
        val f = grab()
        if (f == null || f.samples == null || f.samples.length == 0) {
          frame.samples = null
          return
        }
        if (f.timestamp + lengthPerFrame >= time) {
          val sb = f.samples(0).asInstanceOf[FloatBuffer]
          val remaining = sb.remaining()
          if (remaining <= FRAME_SIZE) {
            sb.get(output, 0, remaining)
            written = remaining
          } else {
            sb.get(output, 0, FRAME_SIZE)
            written = FRAME_SIZE
            val excess = remaining - FRAME_SIZE
            sb.get(sampleBuf, 0, excess)
            bufLen = excess
          }
          found = true
        }
        i += 1
      }
    }

    // 持续抓取原始帧直至凑够 FRAME_SIZE 个采样点
    var filling = true
    while (written < FRAME_SIZE && filling) {
      val f = grab()
      if (f == null || f.samples == null || f.samples.length == 0) {
        if (written == 0) {
          frame.samples = null
          return
        }
        // 文件末尾不足部分以静音填充
        filling = false
      } else {
        val sb = f.samples(0).asInstanceOf[FloatBuffer]
        val remaining = sb.remaining()
        val need = FRAME_SIZE - written

        if (remaining <= need) {
          sb.get(output, written, remaining)
          written += remaining
        } else {
          sb.get(output, written, need)
          written = FRAME_SIZE
          val excess = remaining - need
          sb.get(sampleBuf, 0, excess)
          bufLen = excess
        }
      }
    }

    frame.samples = output
  }

  override protected def configure(): Unit = {
    grabber.setSampleRate(44100)
    grabber.setAudioChannels(2)
    grabber.setSampleFormat(AV_SAMPLE_FMT_FLT)
  }

  override def codecName: String = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getAudioCodecName
  }

  override def codec: Int = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getAudioCodec
  }

  override def lengthPerFrame: Long = {
    FRAME_SIZE / 2 * SECOND / 44100
  }
}

object AudDecRes {
  final val FRAME_SIZE = 1024
}
