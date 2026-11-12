package com.lomekwi.cave.timeline

import com.lomekwi.cave.project.TestIds

/**
 * 片段的 [[com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor]] 随取随建。
 */
class SegmentActorTest extends GdxTestBase {

  test("复制片段会重建 actor") {
    val original = new TestCont(Timespan.SECOND.value)
    val originalActor = original.getTlSegmentActor
    assert((originalActor) != null)

    val copy = original.withId(TestIds.nextId())
    assert((copy.getTlSegmentActor) != null)
    assert(!((originalActor) eq (copy.getTlSegmentActor)))
  }
}
