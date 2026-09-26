package com.lomekwi.cave.timeline

import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.ui.editpanel.tlarea.TlSrcActor

/** 测试用源 Actor 桩，不创建任何 UI/资源。 */
class TlTestSrcActor(source: Source[?]) extends TlSrcActor(source) {
}
