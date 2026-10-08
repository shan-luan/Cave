package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.audio.{AudSource, GainNode}
import com.lomekwi.cave.pipeline.text.TextFilter
import com.lomekwi.cave.pipeline.text.TextSource
import com.lomekwi.cave.resource.media.{AudRes, VdoRes}
import com.lomekwi.cave.timeline.GdxTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, ObjectInputStream, ObjectOutputStream}
import javax.sound.sampled.{AudioFileFormat, AudioFormat, AudioInputStream, AudioSystem}
import scala.util.Using

/**
 * 复制崩溃的第二批分层复现。覆盖文本片段、视频片段与滤镜链深层结构。
 */
class SegmentDuplicateRepro2Test extends GdxTestBase {

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
    writeSineWav(wav, 44100, 0.2)
    new AudRes(wav.getAbsolutePath)
  }

  private def newVdoRes(): VdoRes = {
    val mp4 = File.createTempFile("cave-repro", ".mp4")
    mp4.deleteOnExit()
    val pb = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error",
      "-f", "lavfi", "-i", "testsrc=duration=1:size=128x128:rate=30",
      "-pix_fmt", "yuv420p", mp4.getAbsolutePath)
    assumeTrue(pb.start().waitFor() == 0, "ffmpeg 不可用，跳过视频复现")
    new VdoRes(mp4.getAbsolutePath)
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
  def level9_textSegment_serializes(): Unit = {
    assumeDecoderAvailable()
    val segment = new Boundless(new TextSource("测试文本"))
    segment.attach(new TextFilter)
    serialize(segment)
  }

  @Test
  def level10_vdoClipWithImageFilters_serializes(): Unit = {
    assumeDecoderAvailable()
    val segment = new Clip(new com.lomekwi.cave.pipeline.image.VdoSource(newVdoRes()))
    segment.attach(new com.lomekwi.cave.pipeline.image.TransNode)
    segment.attach(new com.lomekwi.cave.pipeline.image.OpacityNode)
    serialize(segment)
  }

  @Test
  def level11_vdoClipWithNodeGraph_serializes(): Unit = {
    assumeDecoderAvailable()
    val segment = new Clip(new com.lomekwi.cave.pipeline.image.VdoSource(newVdoRes()))
    val graph = new NodeGraphFilter
    val time = new com.lomekwi.cave.pipeline.num.TimeNode
    val add = new com.lomekwi.cave.pipeline.num.AddNode
    graph.innerNodes.add(time)
    graph.innerNodes.add(add)
    add.inA.linkFrom(time.segmentOut)
    segment.attach(graph)
    serialize(segment)
  }

  @Test
  def level12_mixedSelection_segmentsSerializeIndependently(): Unit = {
    assumeDecoderAvailable()
    val text = new Boundless(new TextSource("测试文本"))
    text.attach(new TextFilter)
    val audio = new Clip(new AudSource(newAudRes()))
    audio.attach(new GainNode)
    serialize(text)
    serialize(audio)
    serialize(text)
  }
}
