package com.lomekwi.cave.resource.media

import com.lomekwi.cave.resource.decoder.DecRes
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.DoubleAdder
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

/**
 * 按消费者键池化的解码器池。
 */
final class DecoderPool(factory: () => DecRes[?], val weight: Double) {

  private val entries = new ConcurrentHashMap[AnyRef, PooledDec]()

  DecoderPool.registerPool(this)

  /** 借出 consumer 专用的解码器。 */
  @tailrec
  def acquire(consumer: AnyRef): DecoderLease = {
    val pd = entries.get(consumer)
    if (pd == null) {
      create(consumer) match {
        case Some(lease) => lease
        case None        => acquire(consumer)
      }
    } else {
      val lent = pd.synchronized {
        if (entries.get(consumer) ne pd) {
          None
        } else if (pd.refcount == 0) {
          pd.refcount = 1
          Some(new DecoderLease(pd))
        } else {
          throw new IllegalStateException(("解码器被同键消费者并发借出: ".in(Zh) | "Decoder concurrently borrowed by a consumer with the same key: ".in(En)).str() + consumer)
        }
      }
      lent match {
        case Some(lease) => lease
        case None        => acquire(consumer)
      }
    }
  }

  private def create(consumer: AnyRef): Option[DecoderLease] = {
    DecoderPool.admit(weight)
    val pd = new PooledDec(factory(), weight)
    pd.refcount = 1
    if (entries.putIfAbsent(consumer, pd) == null) {
      DecoderPool.admitWeight(pd)
      Some(new DecoderLease(pd))
    } else {
      try pd.dec.close() catch { case _: Exception => () }
      None
    }
  }

  private[media] def evictIdle(): Unit = {
    val now = System.nanoTime()
    val it = entries.entrySet().iterator()
    while (it.hasNext) {
      val e = it.next()
      val expired = e.getValue.synchronized {
        e.getValue.refcount == 0 && now - e.getValue.lastAccessNanos > DecoderPool.idleNanos
      }
      if (expired) {
        evict(e.getKey, e.getValue)
      }
    }
  }

  private[media] def idleEntries: List[(AnyRef, PooledDec)] = {
    entries.asScala.toList.filter { case (_, pd) => pd.synchronized(pd.refcount == 0) }
  }

  private[media] def evict(key: AnyRef, pd: PooledDec): Boolean = {
    val removed = pd.synchronized(pd.refcount == 0 && entries.remove(key, pd))
    if (removed) {
      DecoderPool.retireWeight(pd)
      try pd.dec.close() catch { case _: Exception => () }
    }
    removed
  }

  /** 关闭全部条目。 */
  private[media] def disposeAll(): Unit = {
    DecoderPool.unregisterPool(this)
    val it = entries.values().iterator()
    while (it.hasNext) {
      val pd = it.next()
      it.remove()
      DecoderPool.retireWeight(pd)
      try pd.dec.close() catch { case _: Exception => () }
    }
  }
}

/** 池中一个解码器的占位。 */
private[media] final class PooledDec(val dec: DecRes[?], val weight: Double) {
  var refcount: Int = 0
  @volatile var lastAccessNanos: Long = 0L
}

/** 一次借出的持有凭证。 */
final class DecoderLease private[media] (private val pooled: PooledDec) extends AutoCloseable {
  def dec: DecRes[?] = pooled.dec
  override def close(): Unit = {
    pooled.synchronized {
      pooled.refcount = 0
      pooled.lastAccessNanos = System.nanoTime()
    }
  }
}

private[media] object DecoderPool {
  private val idleNanos: Long = TimeUnit.SECONDS.toNanos(30)
  private val weightBudget: Double = 8.0

  private val pools: java.util.Set[DecoderPool] = ConcurrentHashMap.newKeySet[DecoderPool]()
  private val totalWeight: DoubleAdder = new DoubleAdder()

  private val CLEANUP: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r: Runnable) => {
    val t = new Thread(r, "decoder-pool-cleanup")
    t.setDaemon(true)
    t
  })

  CLEANUP.scheduleWithFixedDelay(() => {
    for (pool <- pools.asScala) {
      pool.evictIdle()
    }
  }, 30, 15, TimeUnit.SECONDS)

  private[media] def registerPool(pool: DecoderPool): Unit = {
    pools.add(pool)
  }

  private[media] def unregisterPool(pool: DecoderPool): Unit = {
    pools.remove(pool)
  }

  private[media] def admitWeight(pd: PooledDec): Unit = {
    totalWeight.add(pd.weight)
  }

  private[media] def retireWeight(pd: PooledDec): Unit = {
    totalWeight.add(-pd.weight)
  }

  /** 新建前回收全局空闲预算。 */
  private[media] def admit(incoming: Double): Unit = {
    if (totalWeight.sum() + incoming <= weightBudget) return
    val candidates = pools.asScala.toList
      .flatMap(p => p.idleEntries.map(e => (p, e._1, e._2)))
      .sortBy(_._3.lastAccessNanos)
    for ((pool, key, pd) <- candidates) {
      if (totalWeight.sum() + incoming <= weightBudget) return
      pool.evict(key, pd)
    }
  }
}
