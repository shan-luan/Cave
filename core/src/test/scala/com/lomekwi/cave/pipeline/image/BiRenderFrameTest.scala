package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BiRenderFrameTest {

  private class RecordingRenderable(calls: StringBuilder, tag: Char) extends Renderable {
    override def render(batch: Batch): Unit = {
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
}
