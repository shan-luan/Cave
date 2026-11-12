package com.lomekwi.cave.pipeline.text

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.lomekwi.cave.pipeline.{Source, Node}
import com.lomekwi.cave.pipeline.image.Transform
import com.lomekwi.cave.resource.media.FontRes
import com.lomekwi.cave.timeline.{Segment, Timespan, Track}
import com.lomekwi.cave.ui.editpanel.tlarea.{TlSegmentActor, TlTextSegmentActor}

import java.util.concurrent.CountDownLatch
import scala.compiletime.uninitialized

@SerialVersionUID(1L)
class TextSource(text: String) extends Source[TextFrame] {
  private final val textIn: Node.InPort[String] = addInPort(
    new Node.InPort[String](("文本".in(Zh) | "Text".in(En)).str(), ("请输入文本".in(Zh) | "Enter text".in(En)).str(), classOf[String]) {})
  private final val fontSizeIn: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("字号".in(Zh) | "Font Size".in(En)).str(), 48.0, classOf[Double]) {})
  @transient private var fontRes: FontRes = uninitialized
  @transient private var font: BitmapFont = uninitialized
  @transient private var generatedFontSize: Int = 0
  @volatile @transient private var initialized: Boolean = false

  textIn.defaultData = text
  fontRes = new FontRes("font/noto.otf")

  def this() = {
    this(("请输入文本".in(Zh) | "Enter text".in(En)).str())
  }

  def getText: String = {
    textIn.defaultData
  }

  def setText(text: String): Unit = {
    textIn.defaultData = text
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
    fontSizeIn.defaultData.toInt
  }

  def setFontSize(fontSize: Int): Unit = {
    fontSizeIn.defaultData = fontSize.toDouble
    invalidateFont()
  }

  def getFontPath: String = {
    if (fontRes != null) fontRes.path else ""
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

  override protected def produce(time: Timespan, track: Track, segment: Segment): TextFrame = {
    if (frame != null && frame.trackIndex != track.index) {
      initialized = false
    }
    if (font != null && generatedFontSize != getFontSize) {
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
        frame = new TextFrame(track.index, segment.id)
        frame.setFont(font)
        frame.transform = new Transform(0, 0, 0)
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
    frame.transform.reset(0, 0)
    frame.opacity = 1f
    frame
  }

  override def getLengthPerExportFrame: Timespan = {
    Timespan.SECOND
  }

  override def getDuration: Timespan = {
    Timespan.MaxValue
  }

  override def getDefaultDuration: Option[Timespan] = {
    Some(Timespan.SECOND * 5)
  }

  override def displayName: String = {
    ("文本源".in(Zh) | "Text Source".in(En)).str()
  }

  override def createTlSegmentActor(segment: Segment): TlSegmentActor = {
    new TlTextSegmentActor(segment)
  }

  override def onDuplicate(original: Source[?]): Unit = {
    val source = original.asInstanceOf[TextSource]
    this.textIn.defaultData = source.getText
    this.fontSizeIn.defaultData = source.getFontSize.toDouble
    this.fontRes = new FontRes(source.fontRes.path)
  }
}
