package com.lomekwi.cave.timeline

import com.lomekwi.cave.util.Units.SECOND

import org.junit.jupiter.api.Assertions.{assertNotSame, assertNotNull}
import org.junit.jupiter.api.Test

/**
 * 片段的 `TlSegmentActor` 随取随建，且不参与序列化：
 * [[com.lomekwi.cave.util.Duplicatable.duplicate]] 以序列化产出副本，
 * 副本首次访问必须重建 actor，而不是沿用原片段的实例。
 */
class SegmentActorTest extends GdxTestBase {

  @Test
  def duplicateRebuildsActor(): Unit = {
    val original = new TestCont(SECOND)
    val originalActor = original.getTlSegmentActor
    assertNotNull(originalActor, "首次访问应创建 actor")

    val copy = original.duplicate()
    assertNotNull(copy.getTlSegmentActor, "副本应重建 actor，而不是得到 null")
    assertNotSame(originalActor, copy.getTlSegmentActor, "副本不应与原片段共享 actor")
  }
}
