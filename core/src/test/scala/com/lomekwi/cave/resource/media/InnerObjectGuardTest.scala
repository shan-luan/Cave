package com.lomekwi.cave.resource.media

/**
 * 内嵌单例守卫。类内的 object 会被 Scala 编码为宿主实例的非 transient 惰性字段，
 * 因此这些常量单例必须保持在文件顶层，不得移回类内。
 */
class InnerObjectGuardTest extends munit.FunSuite {

  test("VdoRes 不持有单例实例字段") {
    val bad = classOf[VdoRes].getDeclaredFields.map(_.getName).filter(_.contains("Thumbnailer"))
    assert(bad.isEmpty, "VdoRes 不应持有内嵌 Thumbnailer 单例的实例字段: " + bad.mkString(","))
  }

  test("AudRes 不持有单例实例字段") {
    val bad = classOf[AudRes].getDeclaredFields.map(_.getName)
      .filter(n => n.contains("Waveformer") || n.contains("SingleWaveform"))
    assert(bad.isEmpty, "AudRes 不应持有内嵌单例的实例字段: " + bad.mkString(","))
  }
}
