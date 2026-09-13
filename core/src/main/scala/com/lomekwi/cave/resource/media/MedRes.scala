package com.lomekwi.cave.resource.media

import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.resource.Resource
import com.lomekwi.cave.resource.decoder.DecRes

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalListener
import com.google.common.cache.RemovalNotification

import java.io.ObjectInputStream
import java.io.Serializable
import java.util.Set
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 *媒体资源类，指代一个在磁盘中存在，占有编解码器的资源
 */
@SerialVersionUID(1L)
abstract class MedRes(private val path: String) extends Resource with Serializable {
  import MedRes.*

  protected var duration: Long = 0
  protected var codecName: String = null
  protected var codec: Int = 0

  @transient private var decoderCache: Cache[Integer, DecRes[?]] = CacheBuilder.newBuilder()
    .asInstanceOf[CacheBuilder[Integer, DecRes[?]]]
    .expireAfterAccess(DECODER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .removalListener(new RemovalListener[Integer, DecRes[?]] {
      override def onRemoval(notification: RemovalNotification[Integer, DecRes[?]]): Unit = {
        val dec = notification.getValue()
        if (dec != null) {
          try { dec.close() } catch { case ignored: Exception => () }
        }
      }
    })
    .build()

  /**
   * 必须确保路径对应一个存在的文件
   */
  instances.add(this)
  try {
    Using.resource(newDecoder()) { metadataDecRes =>
      metadataDecRes.start()
      generateMetadata(metadataDecRes)
      this.duration = Math.max(0, metadataDecRes.getLengthInTime())
    }
  } catch {
    case e: Exception =>
      throw new RuntimeException(e)
  }

  def getDuration(): Long = {
    duration
  }

  def getPath(): String = {
    path
  }

  def getCodecName(): String = {
    codecName
  }

  def getCodec(): Int = {
    codec
  }
  def getDecoder(trackIndex: Int): DecRes[?] = {
    try {
      decoderCache.get(trackIndex, () => newDecoder())
    } catch {
      case e: ExecutionException =>
        throw new RuntimeException(e)
    }
  }

  def get(trackIndex: Int, time: Long, frame: Frame): Unit = {
    getDecoder(trackIndex).asInstanceOf[DecRes[Frame]].get(time, frame)
  }
  def sync(trackIndex: Int, time: Long): Unit = {
    getDecoder(trackIndex).sync(time)
  }

  override def close(): Unit = {
    instances.remove(this)
    decoderCache.invalidateAll()
  }

  private def readObject(ois: ObjectInputStream): Unit = {
    ois.defaultReadObject()
    instances.add(this)
    decoderCache = CacheBuilder.newBuilder()
      .asInstanceOf[CacheBuilder[Integer, DecRes[?]]]
      .expireAfterAccess(DECODER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .removalListener(new RemovalListener[Integer, DecRes[?]] {
        override def onRemoval(notification: RemovalNotification[Integer, DecRes[?]]): Unit = {
          val dec = notification.getValue()
          if (dec != null) {
            try { dec.close() } catch { case ignored: Exception => () }
          }
        }
      })
      .build()
  }
  protected def newDecoder(): DecRes[?]
  protected def generateMetadata(metadataDecRes: DecRes[?]): Unit
}

object MedRes {
  private final val DECODER_TIMEOUT_SECONDS = 30

  private final val instances: Set[MedRes] = ConcurrentHashMap.newKeySet[MedRes]()

  private final val CLEANUP: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r: Runnable) => {
    val t = new Thread(r, "decoder-cache-cleanup")
    t.setDaemon(true)
    t
  })

  CLEANUP.scheduleWithFixedDelay(() => {
    for (res <- instances.asScala) {
      res.decoderCache.cleanUp()
    }
  }, DECODER_TIMEOUT_SECONDS, 15, TimeUnit.SECONDS)
}
