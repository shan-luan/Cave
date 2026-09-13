package com.lomekwi.cave.resource.media

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.audio.AudFrame
import com.lomekwi.cave.resource.decoder.AudDecRes
import com.lomekwi.cave.resource.decoder.DecRes
import com.lomekwi.cave.ui.Colors

import java.io.ObjectInputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

@SerialVersionUID(1L)
class AudRes(path: String) extends MedRes(path) with Previewable with Showable {
  private var frameLength: Long = scala.compiletime.uninitialized

  @transient private var waveformerRef: Waveformer = null
  @transient private var singleWaveform: SingleWaveform = null

  override protected def generateMetadata(metadataDecRes: DecRes[?]): Unit = {
    val adr = metadataDecRes.asInstanceOf[AudDecRes]
    frameLength = adr.getLengthPerFrame()
    codecName = adr.getCodecName()
    codec = adr.getCodec()
  }

  override protected def newDecoder(): AudDecRes = {
    new AudDecRes(this)
  }

  def getFrameLength(): Long = {
    frameLength
  }

  private def getWaveformer(): Waveformer = {
    if (waveformerRef == null) {
      waveformerRef = new Waveformer()
    }
    waveformerRef
  }

  def waveformer(): Waveformer = {
    getWaveformer()
  }

  override def getPreview(time: Long): Texture = {
    getWaveformer().queueSlot(time)
    getWaveformer().getTexture()
  }

  override def getPreviewInterval(): Long = {
    getWaveformer().bucketDuration
  }

  override def getPreview(): Texture = {
    getSingleWaveform().get()
  }

  private def getSingleWaveform(): SingleWaveform = {
    if (singleWaveform == null) {
      singleWaveform = new SingleWaveform()
    }
    singleWaveform
  }

  private def readObject(ois: ObjectInputStream): Unit = {
    ois.defaultReadObject()
    waveformerRef = null
    singleWaveform = null
  }

  override def close(): Unit = {
    super.close()
    if (waveformerRef != null) {
      waveformerRef.dispose()
    }
    if (singleWaveform != null) {
      singleWaveform.dispose()
    }
  }

  private class SingleWaveform {
    import SingleWaveform.*

    private final val generating: AtomicBoolean = new AtomicBoolean(false)
    @transient @volatile private var texture: Texture = null

    private[media] def get(): Texture = {
      if (texture != null) {
        return texture
      }
      if (generating.compareAndSet(false, true)) {
        App.workerExecutor.submit(new Runnable {
          override def run(): Unit = generate()
        })
      }
      null
    }

    private def generate(): Unit = {
      val dec = newDecoder()
      val frame = new AudFrame(44100, 2, null)
      val peaks = new Array[Float](W)
      try {
        dec.start()
        val frameLen = dec.getLengthPerFrame()
        var t = 0L
        var col = 0
        var continueLoop = true
        while (col < W && continueLoop) {
          val colEnd = (col + 1) * duration / W
          dec.get(t, frame)
          val samples = frame.getSamples()
          if (samples == null) {
            continueLoop = false
          } else {
            for (s <- samples) {
              val a = if (s < 0) -s else s
              if (a > peaks(col)) {
                peaks(col) = a
              }
            }
            t += frameLen
            if (t >= colEnd) {
              col += 1
            }
          }
        }

        val pm = new Pixmap(W, H, Pixmap.Format.RGBA8888)
        pm.setColor(0f, 0f, 0f, 0f)
        pm.fill()
        val mid = H / 2
        pm.setColor(Colors.ACCENT)
        for (x <- 0 until W) {
          val p = Math.min(1f, peaks(x))
          val amp = (p * (mid - 2)).toInt
          pm.drawLine(x, mid - amp, x, mid + amp)
        }
        val fp = pm
        Gdx.app.postRunnable(() => {
          if (texture != null) {
            texture.dispose()
          }
          texture = new Texture(fp)
          texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
          fp.dispose()
        })
      } catch {
        case e: Exception =>
          Gdx.app.error("AudRes", "Single waveform failed for " + getPath(), e)
      } finally {
        try {
          dec.close()
        } catch {
          case ignored: Exception => ()
        }
      }
    }

    private[media] def dispose(): Unit = {
      if (texture != null) {
        texture.dispose()
        texture = null
      }
    }
  }

  private object SingleWaveform {
    private final val W = 160
    private final val H = 90
  }

  class Waveformer private[media]() {
    import Waveformer.*

    final val bucketDuration: Long = 1_000_000L / DECIMATED_RATE
    final val totalBuckets: Int = Math.max(1, (duration / 1_000_000L * DECIMATED_RATE).toInt)
    final val texWidth: Int = 512
    final val texHeight: Int = (totalBuckets + texWidth - 1) / texWidth

    @transient var pixmap: Pixmap = null
    @transient var waveTex: Texture = null
    @transient private var queued: Array[Boolean] = null
    @transient @volatile var dirty: Boolean = false

    @transient private var batchSlots: Array[Int] = new Array[Int](BATCH_SIZE)
    @transient private var batchPeaks: Array[Float] = new Array[Float](BATCH_SIZE)
    @transient private var batchCount: Int = 0

    @transient private final val workerRunning: AtomicBoolean = new AtomicBoolean(false)
    @transient private final val pendingSlots: ConcurrentLinkedQueue[Integer] =
      new ConcurrentLinkedQueue[Integer]()

    queued = new Array[Boolean](totalBuckets)

    pixmap = new Pixmap(texWidth, texHeight, Pixmap.Format.RGBA8888)
    pixmap.setColor(0f, 0f, 0f, 1f)
    pixmap.fill()

    Gdx.app.postRunnable(() => {
      waveTex = new Texture(pixmap)
      waveTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
    })

    private[media] def getTexture(): Texture = {
      waveTex
    }

    private[media] def queueSlot(time: Long): Unit = {
      var idx = (time / bucketDuration).toInt
      if (idx < 0) {
        idx = 0
      }
      if (idx >= totalBuckets) {
        idx = totalBuckets - 1
      }
      if (!queued(idx)) {
        queued(idx) = true
        pendingSlots.offer(idx)
        ensureWorker()
      }
    }

    private def ensureWorker(): Unit = {
      if (workerRunning.compareAndSet(false, true)) {
        App.workerExecutor.submit(new Runnable {
          override def run(): Unit = processPendingSlots()
        })
      }
    }

    @transient private var cachedDec: AudDecRes = null

    private def getCachedDecoder(): AudDecRes = {
      if (cachedDec == null) {
        cachedDec = newDecoder()
      }
      cachedDec
    }

    private def processPendingSlots(): Unit = {
      val dec = getCachedDecoder()
      try {
        if (!dec.isInitialized()) {
          dec.start()
        }
        val frame = new AudFrame(44100, 2, null)
        val slots = new Array[Int](BATCH_SIZE)

        var running = true
        while (running) {
          var count = 0
          var i = 0
          while (i < BATCH_SIZE) {
            val idx = pendingSlots.poll()
            if (idx == null) {
              i = BATCH_SIZE
            } else {
              slots(count) = idx
              count += 1
              i += 1
            }
          }
          if (count == 0) {
            running = false
          } else {
            java.util.Arrays.sort(slots, 0, count)
            dec.sync(slots(0).toLong * bucketDuration)

            for (i <- 0 until count) {
              try {
                val t = slots(i).toLong * bucketDuration
                dec.get(t, frame)
                val samples = frame.getSamples()
                if (samples != null) {
                  var max = 0f
                  for (s <- samples) {
                    val abs = if (s < 0) -s else s
                    if (abs > max) {
                      max = abs
                    }
                  }
                  batchSlots(batchCount) = slots(i)
                  batchPeaks(batchCount) = Math.min(max, 1f)
                  batchCount += 1
                  if (batchCount >= BATCH_SIZE) {
                    flushBatch()
                  }
                }
              } catch {
                case ignored: Exception => ()
              }
            }
          }
        }
        flushBatch()
      } catch {
        case e: Exception =>
          Gdx.app.error("AudRes", "Waveform worker failed for " + getPath(), e)
      } finally {
        workerRunning.set(false)
        if (!pendingSlots.isEmpty()) {
          ensureWorker()
        }
      }
    }

    private def flushBatch(): Unit = {
      if (batchCount == 0) {
        return
      }
      val n = batchCount
      val slots = java.util.Arrays.copyOf(batchSlots, n)
      val peaks = java.util.Arrays.copyOf(batchPeaks, n)
      Gdx.app.postRunnable(() => {
        for (i <- 0 until n) {
          val px = slots(i) % texWidth
          val py = slots(i) / texWidth
          pixmap.setColor(peaks(i), 0f, 0f, 1f)
          pixmap.drawPixel(px, py)
        }
        dirty = true
      })
      batchCount = 0
    }

    private[media] def dispose(): Unit = {
      if (cachedDec != null) {
        try {
          cachedDec.close()
        } catch {
          case ignored: Exception => ()
        }
        cachedDec = null
      }
    }
  }

  object Waveformer {
    private[media] final val DECIMATED_RATE = 400
    private final val BATCH_SIZE = 64
  }
}
