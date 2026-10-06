package com.lomekwi.cave.pipeline

import com.lomekwi.cave.pipeline.audio.{AudSource, GainNode}
import com.lomekwi.cave.pipeline.num.{AddNode, RandomNode, TimeNode}
import com.lomekwi.cave.resource.media.AudRes
import com.lomekwi.cave.timeline.GdxTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, ObjectInputStream, ObjectOutputStream}
import javax.sound.sampled.{AudioFileFormat, AudioFormat, AudioInputStream, AudioSystem}
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * 复制崩溃（NotSerializableException java.lang.Object）的分层复现诊断测试。
 * 从最小结构到完整结构逐层序列化，哪层失败问题就在哪层。
 */
class SegmentDuplicateReproTest extends GdxTestBase {

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
  def level1_audRes_serializes(): Unit = {
    assumeDecoderAvailable()
    serialize(newAudRes())
  }

  @Test
  def level2_audClip_serializes(): Unit = {
    assumeDecoderAvailable()
    serialize(new Clip(new AudSource(newAudRes())))
  }

  @Test
  def level3_audClipWithGainNode_serializes(): Unit = {
    assumeDecoderAvailable()
    val segment = new Clip(new AudSource(newAudRes()))
    segment.attach(new GainNode)
    serialize(segment)
  }

  @Test
  def level4_audClipWithNodeGraph_serializes(): Unit = {
    assumeDecoderAvailable()
    val segment = new Clip(new AudSource(newAudRes()))
    segment.attach(new GainNode)

    val graph = new NodeGraphFilter
    val add = new AddNode
    val random = new RandomNode
    val time = new TimeNode
    graph.innerNodes.add(add)
    graph.innerNodes.add(random)
    graph.innerNodes.add(time)
    add.inA.linkFrom(time.segmentOut)
    add.inB.linkFrom(random.out)
    val sink = graph.innerNodes.iterator().asScala.collectFirst { case s: Sink => s }.get
    sink.in.linkFrom(add.out)

    segment.attach(graph)
    serialize(segment)
  }

  @Test
  def level5_full_duplicate(): Unit = {
    assumeDecoderAvailable()
    val wav = File.createTempFile("cave-repro", ".wav")
    wav.deleteOnExit()
    writeSineWav(wav, 44100, 0.2)
    val audRes = new AudRes(wav.getAbsolutePath)
    val segment = new Clip(new AudSource(audRes))
    segment.attach(new GainNode)

    val graph = new NodeGraphFilter
    val add = new AddNode
    val time = new TimeNode
    graph.innerNodes.add(add)
    graph.innerNodes.add(time)
    add.inA.linkFrom(time.segmentOut)
    val sink = graph.innerNodes.iterator().asScala.collectFirst { case s: Sink => s }.get
    sink.in.linkFrom(add.out)
    segment.attach(graph)

    val dup = segment.duplicate()
    assertTrue(dup != null)
  }

  /** 生成一张纯色 PNG。 */
  private def newImgRes(): com.lomekwi.cave.resource.media.ImgRes = {
    val png = File.createTempFile("cave-repro", ".png")
    png.deleteOnExit()
    val img = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB)
    javax.imageio.ImageIO.write(img, "png", png)
    new com.lomekwi.cave.resource.media.ImgRes(png.getAbsolutePath)
  }

  @Test
  def level6_imgClipWithImageFilters_serializes(): Unit = {
    assumeDecoderAvailable()
    val segment = new Boundless(new com.lomekwi.cave.pipeline.image.ImgSource(newImgRes()))
    segment.attach(new com.lomekwi.cave.pipeline.image.TransNode)
    segment.attach(new com.lomekwi.cave.pipeline.image.OpacityNode)
    serialize(segment)
  }

  @Test
  def level7_nestedNodeGraph_serializes(): Unit = {
    assumeDecoderAvailable()
    val segment = new Clip(new AudSource(newAudRes()))
    segment.attach(new GainNode)

    // 内图里再放一个节点图，并穿过它接线
    val outer = new NodeGraphFilter
    val nested = new NodeGraphFilter
    val add = new AddNode
    val time = new TimeNode
    outer.innerNodes.add(nested)
    outer.innerNodes.add(add)
    outer.innerNodes.add(time)
    add.inA.linkFrom(time.segmentOut)
    val nestedSink = nested.innerNodes.iterator().asScala.collectFirst { case s: Sink => s }.get
    // 嵌套图的输入直连本层的加法输出
    nested.filterIn.linkFrom(add.out)
    nestedSink.in.linkFrom(nested.filterOut)
    val outerSink = outer.innerNodes.iterator().asScala.collectFirst { case s: Sink => s }.get
    outerSink.in.linkFrom(nested.filterOut)
    segment.attach(outer)

    val dup = segment.duplicate()
    assertTrue(dup != null)
  }

  @Test
  def level8_deepFilterChain_serializes(): Unit = {
    assumeDecoderAvailable()
    val segment = new Clip(new AudSource(newAudRes()))
    (0 until 6).foreach(_ => segment.attach(new GainNode))
    segment.attach(new NodeGraphFilter)
    serialize(segment)
  }
}
