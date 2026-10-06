package com.lomekwi.cave.resource.media

import com.lomekwi.cave.util.Units.SECOND

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.IntMap
import com.lomekwi.cave.app.App
import com.lomekwi.cave.resource.decoder.DecRes
import com.lomekwi.cave.resource.decoder.VdoDecRes
import org.bytedeco.javacv.Frame

import java.io.ObjectInputStream
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import scala.compiletime.uninitialized


@SerialVersionUID(1L)
class VdoRes(path: String) extends MedRes(path) with Previewable with Showable {
  var width: Int = scala.compiletime.uninitialized
  var height: Int = scala.compiletime.uninitialized
  var frameLength: Long = scala.compiletime.uninitialized

  @transient private var thumbnailer: Thumbnailer = uninitialized

  private def getThumbnailer: Thumbnailer = {
    if (thumbnailer == null) {
      thumbnailer = new Thumbnailer()
    }
    thumbnailer
  }

  override protected def decoderWeight: Double = 1.0

  override protected def generateMetadata(metadataDecRes: DecRes[?]): Unit = {
    val vdr = metadataDecRes.asInstanceOf[VdoDecRes]
    width = vdr.getWidth
    height = vdr.getHeight
    frameLength = vdr.lengthPerFrame
    codecName = vdr.codecName
    codec = vdr.codec
  }

  override protected def newDecoder(): VdoDecRes = {
    new VdoDecRes(this)
  }

  override def getPreview(time: Long): Texture = {
    getThumbnailer.get(time)
  }

  override def getPreview: Texture = {
    getThumbnailer.get(duration / 2)
  }

  override def getPreviewInterval: Long = {
    getThumbnailer.interval
  }

  def getThumbnail(srcTime: Long): Texture = {
    getThumbnailer.get(srcTime)
  }

  def getThumbInterval: Long = {
    getThumbnailer.interval
  }

  override def close(): Unit = {
    super.close()
    if (thumbnailer != null) {
      thumbnailer.dispose()
    }
  }

  private def readObject(ois: ObjectInputStream): Unit = {
    ois.defaultReadObject()
    thumbnailer = null
  }

  private class Thumbnailer {
    import com.lomekwi.cave.resource.media.Thumbnailer.*

    private[media] final val interval: Long = SECOND
    private[media] final val slotCount: Int = (duration / interval).toInt + 1
    private final val cache: IntMap[Texture] = new IntMap[Texture]()
    private final val queued: Array[Boolean] = new Array[Boolean](slotCount)
    private final val workerRunning: AtomicBoolean = new AtomicBoolean(false)
    private final val pendingSlots: ConcurrentLinkedQueue[Integer] =
      new ConcurrentLinkedQueue[Integer]()

    @transient private lazy val fullPixmap: Pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888)
    @transient private lazy val thumbW: Int = Math.max(1, (THUMB_HEIGHT * width.toFloat / height).toInt)

    private final val batchSlots: Array[Int] = new Array[Int](BATCH_SIZE)
    private final val batchPixmaps: Array[Pixmap] = new Array[Pixmap](BATCH_SIZE)
    private var batchCount: Int = 0

    private[media] def get(srcTime: Long): Texture = {
      var idx = (srcTime / interval).toInt
      if (idx < 0) {
        idx = 0
      }
      if (idx >= slotCount) {
        idx = slotCount - 1
      }

      if (!cache.containsKey(idx) && !queued(idx)) {
        queued(idx) = true
        pendingSlots.offer(idx)
        ensureWorker()
      }

      (0 to idx).reverseIterator
        .map(i => cache.get(i))
        .find(t => t != null)
        .orNull
    }

    private def ensureWorker(): Unit = {
      if (workerRunning.compareAndSet(false, true)) {
        App.workerExecutor.execute(() => processPendingSlots())
      }
    }

    private def processPendingSlots(): Unit = {
      val lease = acquire(Thumbnailer.Consumer)
      try {
        val dec = lease.dec.asInstanceOf[VdoDecRes]
        if (!dec.initialized) {
          dec.start()
        }

        var running = true
        while (running) {
          val idx = pendingSlots.poll()
          if (idx == null) {
            running = false
          } else {
            try {
              val t = idx.toLong * interval
              dec.sync(t)
              val f: Frame = dec.grab()
              if (f != null && f.image != null && f.image(0).isInstanceOf[ByteBuffer]) {
                val buf = f.image(0).asInstanceOf[ByteBuffer]
                buf.rewind()
                fullPixmap.getPixels.clear()
                fullPixmap.getPixels.put(buf)

                val small = new Pixmap(thumbW, THUMB_HEIGHT, Pixmap.Format.RGBA8888)
                small.drawPixmap(fullPixmap,
                  0, 0, width, height, 0, 0, thumbW, THUMB_HEIGHT)

                batchSlots(batchCount) = idx
                batchPixmaps(batchCount) = small
                batchCount += 1
                if (batchCount >= BATCH_SIZE) {
                  flushBatch()
                }
              }
            } catch {
              case _: Exception => ()
            }
          }
        }
        flushBatch()
      } catch {
        case e: Exception =>
          Gdx.app.error("VdoRes", "Thumbnail worker failed for " + path, e)
      } finally {
        lease.close()
        workerRunning.set(false)
        if (!pendingSlots.isEmpty) {
          ensureWorker()
        }
      }
    }

    private def flushBatch(): Unit = {
      if (batchCount != 0) {
        val n = batchCount
        val slots = util.Arrays.copyOf(batchSlots, n)
        val pixmaps = util.Arrays.copyOf(batchPixmaps, n)
        Gdx.app.postRunnable(() => {
          for (i <- 0 until n) {
            if (!cache.containsKey(slots(i))) {
              cache.put(slots(i), new Texture(pixmaps(i)))
            }
            pixmaps(i).dispose()
          }
        })
        batchCount = 0
      }
    }

    private[media] def dispose(): Unit = {
      val textureIt = cache.values().iterator()
      while (textureIt.hasNext) {
        textureIt.next().dispose()
      }
    }
  }
}

private[media] object Thumbnailer {
  private[media] final val THUMB_HEIGHT = 80
  private[media] final val BATCH_SIZE = 16

  private[media] final val Consumer: AnyRef = new Object
}
