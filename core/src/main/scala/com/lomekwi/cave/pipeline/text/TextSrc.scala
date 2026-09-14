package com.lomekwi.cave.pipeline.text

import com.lomekwi.cave.util.Units.SECOND

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.num.NumFrame
import com.lomekwi.cave.pipeline.image.Transform
import com.lomekwi.cave.resource.media.FontRes
import com.lomekwi.cave.timeline.Segment
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.previewarea.TransFrameActor
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor
import com.lomekwi.cave.ui.editpanel.tlarea.TextSegActor

import java.util.concurrent.CountDownLatch

@SerialVersionUID(1L)
class TextSrc(text: String) extends Source[TextFrame] {
  private final val textIn: Node.InPort[String] = addInPort(
    new Node.InPort[String]("文本", "请输入文本", classOf[String]) {})
  private final val fontSizeIn: Node.InPort[NumFrame] = addInPort(
    new Node.InPort[NumFrame]("字号", TextSrc.frame(48), classOf[NumFrame]) {})
  @transient private var fontRes: FontRes = null
  @transient private var font: BitmapFont = null
  /** 已生成的字体字号，用于检测端口字号被外部修改后需要重建字体。 */
  @transient private var generatedFontSize: Int = 0
  @transient private var actor: TransFrameActor = null
  @volatile @transient private var initialized: Boolean = false

  textIn.setDefaultData(text)
  fontRes = new FontRes("font/noto.otf")

  def this() = {
    this("请输入文本")
  }

  def getText(): String = {
    textIn.getDefaultData()
  }

  def setText(text: String): Unit = {
    textIn.setDefaultData(text)
  }

  def getFontRes(): FontRes = {
    fontRes
  }

  def setFontRes(fontRes: FontRes): Unit = {
    if (this.fontRes != null && (this.fontRes ne fontRes)) {
      this.fontRes.close()
    }
    this.fontRes = fontRes
  }

  def getFontSize(): Int = {
    fontSizeIn.getDefaultData().getVal().toInt
  }

  def setFontSize(fontSize: Int): Unit = {
    fontSizeIn.getDefaultData().setVal(fontSize.toDouble)
    invalidateFont()
  }

  def getFontPath(): String = {
    if (fontRes != null) fontRes.getPath() else ""
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

  override def sync(time: Long, track: Track): Unit = {}

  override protected def generate(time: Long, track: Track): TextFrame = {
    if (frame != null && (frame.track ne track)) {
      initialized = false
    }
    if (font != null && generatedFontSize != getFontSize()) {
      // spinner 等外部直接改了端口值：丢弃旧字体，重建帧
      font = null
      initialized = false
    }
    val cd = new CountDownLatch(1)
    if (!initialized) {
      Gdx.app.postRunnable(() => {
        if (font == null) {
          font = fontRes.getFont(getFontSize())
          generatedFontSize = getFontSize()
        }
        frame = new TextFrame(track, this)
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
    frame.setText(getText())
    frame.getTransform().reset(0, 0)
    frame
  }

  override def getLengthPerExportFrame(): Long = {
    SECOND
  }

  override def getDuration(): Long = {
    Long.MaxValue
  }

  override def getDefaultSegmentDuration(): Long = {
    5 * SECOND
  }

  override def getFrameType(): Class[TextFrame] = {
    classOf[TextFrame]
  }

  override def getDisplayName(): String = {
    "文本源"
  }

  override def createSegActor(segment: Segment): SegActor = {
    new TextSegActor(segment)
  }

  override def onDuplicate(original: Source[?]): Unit = {
    val src = original.asInstanceOf[TextSrc]
    this.textIn.setDefaultData(src.getText())
    this.fontSizeIn.getDefaultData().setVal(src.getFontSize().toDouble)
    this.fontRes = new FontRes(src.fontRes.getPath())
  }
}

object TextSrc {
  private def frame(v: Double): NumFrame = {
    val f = new NumFrame(null)
    f.setVal(v)
    f
  }
}
