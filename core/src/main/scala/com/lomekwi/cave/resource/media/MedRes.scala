package com.lomekwi.cave.resource.media

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.resource.decoder.DecRes

import java.io.ObjectInputStream
import java.io.Serializable

import scala.compiletime.uninitialized
import scala.util.Using

/**
 * 指代一个在磁盘中存在、占有编解码器的资源。
 */
@SerialVersionUID(1L)
abstract class MedRes(val path: String) extends Resource with Serializable {

  var duration: Long = 0
  var codecName: String = uninitialized
  var codec: Int = 0

  @transient private var pool: DecoderPool = newPool()

  try {
    Using.resource(newDecoder()) { metadataDecRes =>
      metadataDecRes.start()
      generateMetadata(metadataDecRes)
      this.duration = Math.max(0, metadataDecRes.getLengthInTime)
    }
  } catch {
    case e: Exception =>
      throw new RuntimeException(e)
  }

  private def newPool(): DecoderPool = new DecoderPool(() => newDecoder(), decoderWeight)

  /** 借出 consumer 专用的解码器，用完必须 [[DecoderLease.close]] 归还。 */
  def acquire(consumer: AnyRef): DecoderLease = {
    pool.acquire(consumer)
  }

  def get(consumer: AnyRef, time: Long, frame: Frame): Unit = {
    val lease = acquire(consumer)
    try lease.dec.asInstanceOf[DecRes[Frame]].get(time, frame)
    finally lease.close()
  }

  def sync(consumer: AnyRef, time: Long): Unit = {
    val lease = acquire(consumer)
    try lease.dec.sync(time)
    finally lease.close()
  }

  override def close(): Unit = {
    pool.disposeAll()
  }

  private def readObject(ois: ObjectInputStream): Unit = {
    ois.defaultReadObject()
    pool = newPool()
  }

  protected def newDecoder(): DecRes[?]

  /** 本资源的解码器在全局预算中的权重，以视频解码器为 1。覆写必须是常量，池在超类构造期间读取它。 */
  protected def decoderWeight: Double = 0.1

  protected def generateMetadata(metadataDecRes: DecRes[?]): Unit
}
