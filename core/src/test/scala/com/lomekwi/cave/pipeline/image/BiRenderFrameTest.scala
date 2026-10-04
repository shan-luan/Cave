package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BiRenderFrameTest {

  private class RecordingRenderable(calls: StringBuilder, tag: Char) extends Renderable {
    var renderedOpacity: Float = -1f

    override def render(batch: Batch): Unit = {
      renderedOpacity = opacity
      calls.append(tag)
    }

    override protected def createActor(): Actor = null
  }

  @Test
  def renderDrawsBothInHoldingOrder(): Unit = {
    val calls = new StringBuilder
    val frame = new BiRenderFrame(0, new RecordingRenderable(calls, 'a'), new RecordingRenderable(calls, 'b'))

    frame.render(null)

    assertEquals("ab", calls.toString)
  }

  @Test
  def renderSkipsNullSides(): Unit = {
    val calls = new StringBuilder
    val b = new RecordingRenderable(calls, 'b')
    val frame = new BiRenderFrame(0, null, b)

    frame.render(null)

    assertEquals("b", calls.toString)

    val empty = new BiRenderFrame(0, null, null)
    empty.render(null)

    assertEquals("b", calls.toString)
  }

  @Test
  def startsWithDefaultOpacity(): Unit = {
    val frame = new BiRenderFrame(0, new RecordingRenderable(new StringBuilder, 'a'), new RecordingRenderable(new StringBuilder, 'b'))

    assertEquals(1f, frame.opacity, 0f)
  }

  @Test
  def renderMultipliesOwnOpacityIntoSidesAndRestores(): Unit = {
    val a = new RecordingRenderable(new StringBuilder, 'a')
    val b = new RecordingRenderable(new StringBuilder, 'b')
    val frame = new BiRenderFrame(0, a, b)
    frame.opacity = 0.5f
    a.opacity = 0.4f

    frame.render(null)

    // 绘制期间子帧收到自身与帧 opacity 的乘积
    assertEquals(0.2f, a.renderedOpacity, 1e-6f)
    assertEquals(0.5f, b.renderedOpacity, 1e-6f)
    // 绘制后子帧原值还原，不残留累计
    assertEquals(0.4f, a.opacity, 1e-6f)
    assertEquals(1f, b.opacity, 1e-6f)
  }
}
