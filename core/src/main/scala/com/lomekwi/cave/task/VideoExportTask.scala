package com.lomekwi.cave.task

import com.lomekwi.cave.util.Units.SECOND
import com.lomekwi.cave.util.i18n.I18N.i18n
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.audio.AudClipSrc
import com.lomekwi.cave.pipeline.audio.AudFrame
import com.lomekwi.cave.pipeline.image.Renderable
import com.lomekwi.cave.resource.decoder.AudDecRes
import com.lomekwi.cave.timeline.{Gap, Segment, Timeline, Track}

import org.bytedeco.javacv.FFmpegFrameRecorder

import java.io.File
import java.nio.FloatBuffer
import java.util
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.atomic.AtomicReferenceArray

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

class VideoExportTask(private val timeline: Timeline, outputFile: File, width: Int, height: Int, private val fps: Double, private val bitrate: Int) extends Task {
  private var recorder: FFmpegFrameRecorder = uninitialized
  private var frames: AtomicReferenceArray[Frame] = uninitialized
  private var activeSources: Array[Source[?]] = uninitialized
  private var fb: FrameBuffer = uninitialized
  private var batch: SpriteBatch = uninitialized
  @volatile private var t: Long = 0
  private var frameLen: Long = 0
  private var cvFrame: org.bytedeco.javacv.Frame = uninitialized
  private final val queue: SynchronousQueue[org.bytedeco.javacv.Frame] = new SynchronousQueue[org.bytedeco.javacv.Frame]()
  private var projMatrix: Matrix4 = uninitialized

  recorder = new FFmpegFrameRecorder(outputFile, width, height)
  {
    var i = 0
    for (_ <- timeline.asScala) {
      i += 1
    }
    frames = new AtomicReferenceArray[Frame](i)
    activeSources = new Array[Source[?]](i)
  }
  fb = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true)
  batch = new SpriteBatch()
  frameLen = (SECOND / fps).toLong
  cvFrame = new org.bytedeco.javacv.Frame(width, height, org.bytedeco.javacv.Frame.DEPTH_UBYTE, 4)
  projMatrix = new Matrix4().setToOrtho(0, fb.getWidth.toFloat, fb.getHeight.toFloat, 0, 0, 1)

  override def getProgress: Float = {
    t.toFloat / timeline.getLength
  }

  override def run(): Unit = {
    try {
      recorder.setVideoBitrate(bitrate)
      recorder.setAudioChannels(VideoExportTask.AUDIO_CHANNELS)
      recorder.setSampleRate(VideoExportTask.AUDIO_SAMPLE_RATE)
      recorder.setAudioCodec(AV_CODEC_ID_AAC)
      recorder.setFrameRate(fps)
      recorder.start()
      var i: Int = 0
      while (t < timeline.getLength) {
        i = 0
        for (track <- timeline.asScala) {
          var src = activeSources(i)
          val activeRange = if (src == null) null else track.getRange(src)
          if (src == null || !activeRange.contains(t)) {
            src = track.get(t) match {
              case Segment(source) => source
              case _: Gap => null
            }
            if (src != null) {
              track.syncAt(src, t)
            }
            activeSources(i) = src
          }
          if (src == null) {
            frames.set(i, null)
          } else {
            frames.set(i, track.frameAt(src, t))
          }
          i += 1
        }
        Gdx.app.postRunnable(() => mixVideoFrame())
        recorder.record(queue.take())
        t += frameLen
      }
      exportAudio()
      recorder.stop()
    } catch {
      case e: Exception =>
        throw new RuntimeException(e)
    }
  }

  private def exportAudio(): Unit = {
    val tracks: Array[Track] = timeline.getTracks.toArray(new Array[Track](0))
    val active: Array[Source[?]] = new Array[Source[?]](tracks.length)
    val mixBuf: Array[Float] = new Array[Float](VideoExportTask.AUDIO_FRAME_SIZE)

    var audioT: Long = 0
    while (audioT < timeline.getLength) {
      util.Arrays.fill(mixBuf, 0f)

      var i = 0
      while (i < tracks.length) {
        if (tracks(i).getLength != 0) {
          var src = active(i)
          val activeRange = if (src == null) null else tracks(i).getRange(src)
          if (src == null || !activeRange.contains(audioT)) {
            src = tracks(i).get(audioT) match {
              case Segment(source) => source
              case _: Gap => null
            }
            if (src != null && src.isInstanceOf[AudClipSrc]) {
              tracks(i).syncAt(src, audioT)
            }
            active(i) = src
          }
          if (src != null) {
            val frame = tracks(i).frameAt(src, audioT)
            frame match {
              case af: AudFrame if af.getSamples != null =>
                val samples = af.getSamples
                val len = Math.min(samples.length, mixBuf.length)
                var si = 0
                while (si < len) {
                  mixBuf(si) += samples(si)
                  si += 1
                }
              case _ =>
            }
          }
        }
        i += 1
      }

      var j = 0
      while (j < mixBuf.length) {
        mixBuf(j) = Math.max(-1.0f, Math.min(1.0f, mixBuf(j)))
        j += 1
      }

      recorder.setTimestamp(audioT)
      recorder.recordSamples(FloatBuffer.wrap(mixBuf))
      audioT += VideoExportTask.AUDIO_FRAME_DURATION
    }
  }

  private def mixVideoFrame(): Unit = {
    fb.begin()

    batch.begin()
    batch.setProjectionMatrix(projMatrix)
    Gdx.gl.glClearColor(0, 0, 0, 1)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    var i = frames.length() - 1
    while (i >= 0) {
      val frame = frames.get(i)
      frame match {
        case r: Renderable =>
          r.render(batch)
        case _ =>
      }
      i -= 1
    }
    batch.end()

    Gdx.gl.glReadPixels(0, 0, fb.getWidth, fb.getHeight, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, cvFrame.image(0))

    fb.end()
    cvFrame.timestamp = t
    try {
      queue.put(cvFrame)
    } catch {
      case _: InterruptedException =>
        // ignore
    }
  }
  override def close(): Unit = {
    Gdx.app.postRunnable(() => {
      fb.dispose()
      batch.dispose()
    })
    timeline.project.close() //这里的project是反序列化出来的副本，所以可以关闭而不影响用户编辑中的项目。
    cvFrame.close()
    recorder.close()
  }
  override def getName: String = {
    i18n("视频导出：") + timeline.project.name
  }
}

object VideoExportTask {
  private final val AUDIO_FRAME_SIZE = AudDecRes.FRAME_SIZE
  private final val AUDIO_SAMPLE_RATE = 44100
  private final val AUDIO_CHANNELS = 2
  private final val AUDIO_FRAME_DURATION = AUDIO_FRAME_SIZE * SECOND / AUDIO_SAMPLE_RATE / AUDIO_CHANNELS
}
