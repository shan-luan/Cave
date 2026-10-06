package com.lomekwi.cave.resource.media

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 内嵌单例守卫。类内的 object 会被 Scala 编码为宿主实例的非 transient 惰性字段，
 * 一旦宿主实例访问过该单例（如播放视频生成缩略图），字段随实例进入序列化流，
 * 其中的锁对象（裸 java.lang.Object）不可序列化，曾导致片段复制崩溃。
 * 因此这些常量单例必须保持在文件顶层，不得移回类内。
 */
class InnerObjectGuardTest {

  @Test
  def vdoRes_hasNoSingletonInstanceFields(): Unit = {
    val bad = classOf[VdoRes].getDeclaredFields.map(_.getName).filter(_.contains("Thumbnailer"))
    assertTrue(bad.isEmpty, "VdoRes 不应持有内嵌 Thumbnailer 单例的实例字段: " + bad.mkString(","))
  }

  @Test
  def audRes_hasNoSingletonInstanceFields(): Unit = {
    val bad = classOf[AudRes].getDeclaredFields.map(_.getName)
      .filter(n => n.contains("Waveformer") || n.contains("SingleWaveform"))
    assertTrue(bad.isEmpty, "AudRes 不应持有内嵌单例的实例字段: " + bad.mkString(","))
  }
}
