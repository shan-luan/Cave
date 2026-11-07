package com.lomekwi.cave.timeline

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration

/**
 * 为纯模型测试提供 headless 的 libGDX 环境（Gdx.app 等）。
 */
abstract class GdxTestBase extends munit.FunSuite {
  override def beforeAll(): Unit = GdxTestBase.initGdx()
}

object GdxTestBase {
  private var initialized: Boolean = false

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
        throw new IllegalStateException("无法初始化 headless Gdx", t)
    }
  }
}
