package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.Clip
import com.lomekwi.cave.pipeline.audio.{AudSource, GainNode}
import com.lomekwi.cave.project.TestProject
import com.lomekwi.cave.resource.media.AudRes
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, ObjectInputStream, ObjectOutputStream}
import javax.sound.sampled.{AudioFileFormat, AudioFormat, AudioInputStream, AudioSystem}
import scala.util.Using

/**
 * 复制崩溃的运行期状态复现：音频片段先在真实轨道上按播放路径求值，再执行复制。
 * 视频与文本源的产帧依赖 GL 线程，headless 无法模拟，不在本测试覆盖内。
 */
class EvaluatedSegmentCopyTest extends GdxTestBase {

  private def writeSineWav(file: File, sampleRate: Int, seconds: Double): Unit = {
    val frames = (sampleRate * seconds).toInt
    val pcm = new Array[Byte](frames * 2)
    var i = 0
    while (i < frames) {
      val v = (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 8000).toInt.toShort
      pcm(i * 2) = (v & 0xff).toByte
      pcm(i * 2 + 1) = ((v >> 8) & 0xff).toByte
      i += 1
    }
    val format = new AudioFormat(sampleRate.toFloat, 16, 1, true, false)
    val ais = new AudioInputStream(new ByteArrayInputStream(pcm), format, frames.toLong)
    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file)
  }

  private def newAudRes(): AudRes = {
    val wav = File.createTempFile("cave-repro", ".wav")
    wav.deleteOnExit()
    writeSineWav(wav, 44100, 0.5)
    new AudRes(wav.getAbsolutePath)
  }

  private def serialize(obj: AnyRef): Unit = {
    Using.resource(new ByteArrayOutputStream()) { baos =>
      Using.resource(new ObjectOutputStream(baos)) { oos => oos.writeObject(obj) }
      Using.resource(new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray))) { ois =>
        assertTrue(ois.readObject() != null)
      }
    }
  }


  /** ffmpeg 原生库不可用时跳过测试（原生包只声明在 lwjgl3）。 */
  private def assumeDecoderAvailable(): Unit = {
    try Class.forName("org.bytedeco.ffmpeg.global.avutil")
    catch { case _: Throwable =>
      org.junit.jupiter.api.Assumptions.assumeTrue(false, "ffmpeg 原生库不可用，跳过")
    }
  }

  @Test
  def evaluatedAudClip_duplicate(): Unit = {
    assumeDecoderAvailable()
    val project = new TestProject()
    val timeline = project.timeline
    val segment = new Clip(new AudSource(newAudRes()))
    segment.attach(new GainNode)
    timeline.addOrThrow(timeline.getTrackOrCreate(0), segment, new Interval(0, 500 * 1000), 0)
    val track = timeline.getTrackOrCreate(0)

    // 播放路径求值若干时刻，池与帧等运行期状态建立后再复制
    track.frameAt(segment, 0)
    track.frameAt(segment, 250 * 1000)
    track.frameAt(segment, 499 * 1000)

    val dup = segment.duplicate()
    assertTrue(dup != null)
    serialize(dup)
  }
}
