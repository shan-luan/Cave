package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.Segment
import com.lomekwi.cave.ui.editpanel.tlarea.TlSegmentActor

/** 测试用源 Actor 桩，不创建任何 UI/资源。 */
class TlTestSegmentActor(segment: Segment[?]) extends TlSegmentActor(segment) {
}
