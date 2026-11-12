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
  private[text] final val textIn: Node.InPort[String] = addInPort(
    new Node.InPort[String](("文本".in(Zh) | "Text".in(En)).str(), text, classOf[String]) {})
  private[text] final val fontSizeIn: Node.InPort[Double] = addInPort(
    new Node.InPort[Double](("字号".in(Zh) | "Font Size".in(En)).str(), 48.0, classOf[Double]) {})
  @transient private var fontRes: FontRes = uninitialized
  @transient private var font: BitmapFont = uninitialized
  // 已生成的字体字号，用于检测端口字号被外部修改后需要重建字体
  @transient private var generatedFontSize: Int = 0
  @volatile @transient private var initialized: Boolean = false

  fontRes = new FontRes("font/noto.otf")

  def this() = {
    this(("请输入文本".in(Zh) | "Enter text".in(En)).str())
  }

  def getText(segment: Segment): String = {
    segment.filterChain.defaultOf(textIn).asInstanceOf[String]
  }

  private def getFontSize(segment: Segment): Int = {
    segment.filterChain.defaultOf(fontSizeIn).asInstanceOf[Double].toInt
  }

  def getFontRes: FontRes = {
    fontRes
  }

  //FIXME：原地改资源且不标脏/不进 undo；目前没有调用点，若启用需改成 withX 并回写片段。
  def setFontRes(fontRes: FontRes): Unit = {
    if (this.fontRes != null && (this.fontRes ne fontRes)) {
      this.fontRes.close()
    }
    this.fontRes = fontRes
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
    val fontSize = getFontSize(segment)
    if (font != null && generatedFontSize != fontSize) {
      // spinner 等外部直接改了端口值，丢弃旧字体并重建帧
      font = null
      initialized = false
    }
    val cd = new CountDownLatch(1)
    if (!initialized) {
      Gdx.app.postRunnable(() => {
        if (font == null) {
          font = fontRes.getFont(fontSize)
          generatedFontSize = fontSize
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
    frame.setText(getText(segment))
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
    this.fontRes = new FontRes(source.fontRes.path)
  }
}
