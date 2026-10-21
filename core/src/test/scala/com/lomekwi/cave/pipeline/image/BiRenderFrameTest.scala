package com.lomekwi.cave.pipeline.image

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor

class BiRenderFrameTest extends munit.FunSuite {

  private class RecordingRenderable(calls: StringBuilder, tag: Char) extends Renderable {
    var renderedOpacity: Float = -1f

    override def render(batch: Batch): Unit = {
      renderedOpacity = opacity
      calls.append(tag)
    }

    override protected def createActor(): Actor = null
  }

  test("按保持顺序绘制两侧") {
    val calls = new StringBuilder
    val frame = new BiRenderFrame(0, new RecordingRenderable(calls, 'a'), new RecordingRenderable(calls, 'b'))

    frame.render(null)

    assertEquals(calls.toString, "ab")
  }

  test("空侧不参与绘制") {
    val calls = new StringBuilder
    val b = new RecordingRenderable(calls, 'b')
    val frame = new BiRenderFrame(0, null, b)

    frame.render(null)

    assertEquals(calls.toString, "b")

    val empty = new BiRenderFrame(0, null, null)
    empty.render(null)

    assertEquals(calls.toString, "b")
  }

  test("初始使用默认透明度") {
    val frame = new BiRenderFrame(0, new RecordingRenderable(new StringBuilder, 'a'), new RecordingRenderable(new StringBuilder, 'b'))

    assertEqualsFloat(frame.opacity, 1f, 0f)
  }

  test("渲染时把自身透明度乘入两侧并恢复") {
    val a = new RecordingRenderable(new StringBuilder, 'a')
    val b = new RecordingRenderable(new StringBuilder, 'b')
    val frame = new BiRenderFrame(0, a, b)
    frame.opacity = 0.5f
    a.opacity = 0.4f

    frame.render(null)

    // 绘制期间子帧收到自身与帧 opacity 的乘积
    assertEqualsFloat(a.renderedOpacity, 0.2f, 1e-6f)
    assertEqualsFloat(b.renderedOpacity, 0.5f, 1e-6f)
    assertEqualsFloat(a.opacity, 0.4f, 1e-6f)
    assertEqualsFloat(b.opacity, 1f, 1e-6f)
  }
}
