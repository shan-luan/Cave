package com.lomekwi.cave.ui.editpanel.tlarea

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lomekwi.cave.pipeline.audio.AudSource
import com.lomekwi.cave.resource.media.AudRes
import com.lomekwi.cave.timeline.{Segment, Timespan}
import com.lomekwi.cave.timeline.Timespan.*
import com.lomekwi.cave.ui.Colors

class TlAudSegmentActor(segment: Segment) extends TlSegmentActor(segment) {

  override protected def drawContent(batch: Batch, parentAlpha: Float, visibleStartX: Float, visibleEndX: Float): Unit = {
    super.drawContent(batch, parentAlpha, visibleStartX, visibleEndX)

    val res: AudRes = segment.source.asInstanceOf[AudSource].audRes
    val wf: res.Waveformer = res.waveformer()
    val waveTex: Texture = wf.waveTex
    if (waveTex == null) return

    val contentInterval = interval
    val contentOrigin = origin
    val srcLocalStart: Timespan = contentInterval.lo - contentOrigin
    val srcLocalEnd: Timespan = contentInterval.hi - contentOrigin
    val srcDuration: Timespan = srcLocalEnd - srcLocalStart
    if (srcDuration <= Timespan.Zero) return

    val step: Timespan = wf.bucketDuration
    val pxPerUs: Float = getWidth / srcDuration.value.toFloat
    val gridOrigin: Timespan = srcLocalStart.floorTo(step)
    val absVisibleStart: Timespan = srcLocalStart + (visibleStartX / pxPerUs).micros
    val absVisibleEnd: Timespan = srcLocalStart + (visibleEndX / pxPerUs).micros
    var firstT: Timespan = (absVisibleStart - gridOrigin).floorTo(step) + gridOrigin
    firstT = gridOrigin.max(firstT)
    val lastT: Timespan = srcLocalEnd.min(absVisibleEnd)
    var t: Timespan = firstT
    while (t < lastT) {
      res.getPreview(t)
      t += step
    }

    if (wf.dirty) {
      waveTex.draw(wf.pixmap, 0, 0)
      wf.dirty = false
    }

    val bucketUs: Timespan = wf.bucketDuration
    val totalBuckets: Int = wf.totalBuckets
    if (totalBuckets <= 0) return

    val startBucket: Float = srcLocalStart.value.toFloat / bucketUs.value
    val endBucket: Float = startBucket + srcDuration.value.toFloat / bucketUs.value

    val shader: ShaderProgram = TlAudSegmentActor.waveShader
    if (!shader.isCompiled) return

    batch.setShader(shader)
    shader.setUniformf("u_texWidth", wf.texWidth.toFloat)
    shader.setUniformf("u_texHeight", wf.texHeight.toFloat)
    shader.setUniformf("u_startBucket", startBucket)
    shader.setUniformf("u_endBucket", endBucket)
    shader.setUniformf("u_totalBuckets", totalBuckets.toFloat)
    shader.setUniformf("u_color",
      Colors.ACCENT.r, Colors.ACCENT.g, Colors.ACCENT.b, Colors.ACCENT.a * parentAlpha)

    batch.draw(waveTex, getX, getY, getWidth, getHeight)
    batch.setShader(null)
  }
}

object TlAudSegmentActor {
  private lazy val waveShader: ShaderProgram = {
    val shader = new ShaderProgram(VERT, FRAG)
    if (!shader.isCompiled) {
      Gdx.app.error("TlAudSegmentActor", "Wave shader failed:\n" + shader.getLog)
    }
    shader
  }

  private val VERT =
    """|attribute vec4 a_position;
       |attribute vec4 a_color;
       |attribute vec2 a_texCoord0;
       |uniform mat4 u_projTrans;
       |varying vec2 v_texCoord;
       |void main() {
       |    v_texCoord = a_texCoord0;
       |    gl_Position = u_projTrans * a_position;
       |}""".stripMargin

  private val FRAG =
    """|#ifdef GL_ES
       |precision mediump float;
       |#endif
       |varying vec2 v_texCoord;
       |uniform sampler2D u_texture;
       |uniform float u_texWidth;
       |uniform float u_texHeight;
       |uniform float u_startBucket;
       |uniform float u_endBucket;
       |uniform float u_totalBuckets;
       |uniform vec4 u_color;
       |void main() {
       |    float bucket = u_startBucket + v_texCoord.x * (u_endBucket - u_startBucket);
       |    if (bucket < 0.0 || bucket >= u_totalBuckets) { discard; return; }
       |    float pixelIdx = floor(bucket);
       |    float u = mod(pixelIdx, u_texWidth) / u_texWidth;
       |    float v = floor(pixelIdx / u_texWidth) / u_texHeight;
       |    float amp = texture2D(u_texture, vec2(u, v)).r;
       |    float dist = abs(v_texCoord.y - 0.5) * 2.0;
       |    if (dist <= amp) {
       |        gl_FragColor = u_color;
       |    } else {
       |        discard;
       |    }
       |}""".stripMargin
}
