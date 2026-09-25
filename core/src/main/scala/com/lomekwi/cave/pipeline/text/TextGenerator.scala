package com.lomekwi.cave.pipeline.text

import com.lomekwi.cave.util.Units.SECOND

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.lomekwi.cave.pipeline.{Generator, Node, Source}
import com.lomekwi.cave.pipeline.image.Transform
import com.lomekwi.cave.resource.media.FontRes
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.previewarea.TransFrameActor
import com.lomekwi.cave.ui.editpanel.tlarea.{TlSrcActor, TlTextSrcActor}

import java.util.concurrent.CountDownLatch
import scala.compiletime.uninitialized

@SerialVersionUID(1L)
class TextGenerator(text: String) extends Generator[TextFrame] {
  private final val textIn: Node.InPort[String] = addInPort(
    new Node.InPort[String]("文本", "请输入文本", classOf[String]) {})
  private final val fontSizeIn: Node.InPort[Double] = addInPort(
    new Node.InPort[Double]("字号", 48.0, classOf[Double]) {})
  @transient private var fontRes: FontRes = uninitialized
  @transient private var font: BitmapFont = uninitialized
  /** 已生成的字体字号，用于检测端口字号被外部修改后需要重建字体。 */
  @transient private var generatedFontSize: Int = 0
  @transient private var actor: TransFrameActor = uninitialized
  @volatile @transient private var initialized: Boolean = false

  textIn.setDefaultData(text)
  fontRes = new FontRes("font/noto.otf")

  def this() = {
    this("请输入文本")
  }

  def getText: String = {
    textIn.getDefaultData
  }

  def setText(text: String): Unit = {
    textIn.setDefaultData(text)
  }

  def getFontRes: FontRes = {
    fontRes
  }

  def setFontRes(fontRes: FontRes): Unit = {
    if (this.fontRes != null && (this.fontRes ne fontRes)) {
      this.fontRes.close()
    }
    this.fontRes = fontRes
  }

  private def getFontSize: Int = {
    fontSizeIn.getDefaultData.toInt
  }

  def setFontSize(fontSize: Int): Unit = {
    fontSizeIn.setDefaultData(fontSize.toDouble)
    invalidateFont()
  }

  def getFontPath: String = {
    if (fontRes != null) fontRes.getPath else ""
  }

  def setFontPath(path: String): Unit = {
    if (fontRes != null) {
      fontRes.close()
    }
    fontRes = new FontRes(path)
    invalidateFont()
  }

  private def invalidateFont(): Unit = {
    font = null
    initialized = false
  }

  override protected def produce(time: Long, track: Track, source: Source[TextFrame]): TextFrame = {
    if (frame != null && frame.track.index != track.index) {
      initialized = false
    }
    if (font != null && generatedFontSize != getFontSize) {
      // spinner 等外部直接改了端口值，丢弃旧字体并重建帧
      font = null
      initialized = false
    }
    val cd = new CountDownLatch(1)
    if (!initialized) {
      Gdx.app.postRunnable(() => {
        if (font == null) {
          font = fontRes.getFont(getFontSize)
          generatedFontSize = getFontSize
        }
        frame = new TextFrame(track, source)
        frame.setFont(font)
        frame.setTransform(new Transform(0, 0, 0))
        if (actor == null) {
          actor = new TransFrameActor(frame)
        } else {
          actor.rebind(frame)
        }
        frame.setActor(actor)
        initialized = true
        cd.countDown()
      })
      try {
        cd.await()
      } catch {
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
          return null
      }
    }
    frame.setText(getText)
    frame.getTransform.reset(0, 0)
    frame
  }

  override def getLengthPerExportFrame: Long = {
    SECOND
  }

  override def getDuration: Long = {
    Long.MaxValue
  }

  override def getDefaultDuration: Long = {
    5 * SECOND
  }

  override def getDisplayName: String = {
    "文本源"
  }

  override def createTlSrcActor(source: Source[?]): TlSrcActor = {
    new TlTextSrcActor(source)
  }

  override def onDuplicate(original: Generator[?]): Unit = {
    val src = original.asInstanceOf[TextGenerator]
    this.textIn.setDefaultData(src.getText)
    this.fontSizeIn.setDefaultData(src.getFontSize.toDouble)
    this.fontRes = new FontRes(src.fontRes.getPath)
  }
}
