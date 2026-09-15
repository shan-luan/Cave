package com.lomekwi.cave.resource.decoder

import com.badlogic.gdx.Gdx
import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.resource.media.MedRes

import org.bytedeco.javacv.FFmpegFrameGrabber

/**
 * 解码器类。
 * @tparam F 产生的帧类型
 */
abstract class DecRes[F <: Frame] protected (protected val source: MedRes) extends Resource {
  protected final val grabber: FFmpegFrameGrabber = new FFmpegFrameGrabber(source.getPath)
  @volatile protected var initialized: Boolean = false

  def start(): Unit = this.synchronized {
    if (!initialized) {
      if (source.getCodecName != null) {
        grabber.setVideoCodecName(tryGetDecoder())
      }
      configure()
      grabber.start()
      initialized = true
      Gdx.app.debug("DecRes", this.toString + "初始化")
    }
  }
  private def tryGetDecoder(): String = {
    null
  }
  protected def configure(): Unit
  def stop(): Unit = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.stop()
  }
  override def close(): Unit = {
    if (!initialized) {
      return
    }
    grabber.stop()
    grabber.close()
  }
  def grab(): org.bytedeco.javacv.Frame

  /**
   * 同步到指定时间
   * @param time 局部时间
   */
  def sync(time: Long): Unit

  /**
   * 解码指定时间的数据并更新到提供的帧对象中。
   * @param time  局部时间
   * @param frame 要更新的帧对象
   */
  def get(time: Long, frame: F): Unit

  def seek(time: Long): Unit
  def isInitialized: Boolean = {
    initialized
  }
  protected def toValidTime(time: Long): Long = {
    Math.min(Math.max(0, time), getLengthInTime)
  }
  protected def isTimeLegal(time: Long): Boolean = {
    toValidTime(time) == time
  }
  def getLengthInTime: Long = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    Math.max(grabber.getLengthInTime, 0)
  }
  def getTimestamp: Long = {
    if (!initialized) {
      throw new IllegalStateException("Not initialized")
    }
    grabber.getTimestamp
  }
  def getCodecName: String

  def getCodec: Int

  def getLengthPerFrame: Long
  def getLastFrameTime: Long = {
    getTimestamp - getTimestamp % getLengthPerFrame
  }
}
