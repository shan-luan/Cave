package com.lomekwi.cave.timeline

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration

import org.junit.jupiter.api.BeforeAll

/**
 * 为纯模型测试提供 headless 的 libGDX 环境（Gdx.app 等），
 * 避免在构建 Timeline/Track/Segment 时因未初始化 Gdx 而崩溃。
 */
abstract class GdxTestBase {
}

object GdxTestBase {
  private var initialized: Boolean = false

  @BeforeAll
  def initGdx(): Unit = this.synchronized {
    if (initialized) return
    initialized = true
    val cfg = new HeadlessApplicationConfiguration()
    try {
      new HeadlessApplication(new ApplicationListener {
        override def create(): Unit = {}
        override def resize(width: Int, height: Int): Unit = {}
        override def render(): Unit = {}
        override def pause(): Unit = {}
        override def resume(): Unit = {}
        override def dispose(): Unit = {}
      }, cfg)
    } catch {
      case t: Throwable =>
        // 某些环境下 headless 后端不可用；模型测试大多不真正触碰 Gdx。
        throw new IllegalStateException("无法初始化 headless Gdx", t)
    }
  }
}
