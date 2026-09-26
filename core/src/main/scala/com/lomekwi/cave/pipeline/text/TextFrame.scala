package com.lomekwi.cave.pipeline.text

import com.badlogic.gdx.graphics.Color.WHITE

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.Matrix4
import com.lomekwi.cave.pipeline.Frame
import com.lomekwi.cave.pipeline.Segment
import com.lomekwi.cave.pipeline.image.Transform
import com.lomekwi.cave.pipeline.image.Transformable
import com.lomekwi.cave.timeline.Track
import com.lomekwi.cave.ui.editpanel.previewarea.TransFrameActor

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

class TextFrame(trackIndex: Int, segment: Segment[?]) extends Frame(trackIndex, segment) with Transformable {
  @volatile private var text: String = uninitialized
  @volatile private var font: BitmapFont = uninitialized
  private final val layout: GlyphLayout = new GlyphLayout()
  private var transform: Transform = uninitialized
  private var actor: TransFrameActor = uninitialized
  private final val tmpMatrix: Matrix4 = new Matrix4()
  @volatile private var glyphsMissing: Boolean = false
  @volatile private var cachedWidth: Float = 0f
  @volatile private var cachedHeight: Float = 0f
  @volatile private var cachedCenterY: Float = 0f
  @volatile private var version: Int = 0
  private var layoutVersion: Int = 0

  def this(trackIndex: Int) = {
    this(trackIndex, null)
  }

  def setText(text: CharSequence): Unit = {
    this.text = text.toString
    version += 1
  }

  def setFont(font: BitmapFont): Unit = {
    this.font = font
    version += 1
  }

  def setActor(actor: TransFrameActor): Unit = {
    this.actor = actor
  }

  def getActor: TransFrameActor = {
    actor
  }

  override def getTransform: Transform = {
    transform
  }

  override def setTransform(transform: Transform): Unit = {
    this.transform = transform
  }

  override def getBaseWidth: Float = {
    cachedWidth
  }

  override def getBaseHeight: Float = {
    cachedHeight
  }

  override def render(batch: Batch): Unit = {
    if (version != layoutVersion) {
      rebuildLayout()
      layoutVersion = version
    }
    if (font != null && text != null && !glyphsMissing) {
      val t = getTransform
      val scaleX = if (t.isFlipX) -1f else 1f
      val scaleY = if (t.isFlipY) -1f else 1f
      val w = cachedWidth
      val h = cachedHeight

      val saved = new Matrix4(batch.getTransformMatrix)
      val sx = scaleX * t.getScaleX
      val sy = scaleY * t.getScaleY
      tmpMatrix.set(saved)
      tmpMatrix.translate(t.getX + w * sx / 2, t.getY + h * sy / 2, 0)
      tmpMatrix.rotate(0, 0, 1, t.getRotation)
      tmpMatrix.scale(sx, sy, 1)
      batch.setTransformMatrix(tmpMatrix)
      font.setColor(WHITE)
      try {
        font.draw(batch, layout, -w / 2, -cachedCenterY)
      } catch {
        case _: NullPointerException =>
          glyphsMissing = true
      }
      batch.setTransformMatrix(saved)
    }
  }

  private def rebuildLayout(): Unit = {
    val t = text
    val f = font
    if (f == null || t == null || hasMissingGlyph(f, t)) {
      glyphsMissing = true
      cachedWidth = 0
      cachedHeight = 0
      cachedCenterY = 0
    } else {
      font.setColor(WHITE)
      layout.setText(f, t)
      glyphsMissing = false
      cachedWidth = layout.width

      var minBottom = Float.MaxValue
      var maxTop = -Float.MaxValue
      for (run <- layout.runs.asScala) {
        for (glyph <- run.glyphs.asScala) {
          val bottom = run.y + glyph.yoffset
          val top = bottom + glyph.height
          if (bottom < minBottom) minBottom = bottom
          if (top > maxTop) maxTop = top
        }
      }
      if (minBottom > maxTop) {
        cachedHeight = layout.height
        cachedCenterY = font.getAscent
      } else {
        cachedHeight = maxTop - minBottom
        cachedCenterY = font.getAscent + (maxTop + minBottom) / 2f
      }
    }
  }

  /** 文本中含有字体缺少字形的非空白字符。 */
  private def hasMissingGlyph(f: BitmapFont, t: String): Boolean = {
    var i = 0
    var missing = false
    while (i < t.length() && !missing) {
      val c = t.charAt(i)
      if (!(c == ' ' || c == '\n' || c == '\r' || c == '\t')) {
        missing = f.getData.getGlyph(c) == null
      }
      i += 1
    }
    missing
  }
}
