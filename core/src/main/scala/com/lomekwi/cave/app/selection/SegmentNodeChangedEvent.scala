package com.lomekwi.cave.app.selection

import com.lomekwi.cave.pipeline.Segment

/**
 * 某个片段的节点图（滤镜链、端口值、变换节点）被改动，界面按需重建。
 * 与选中无关，改动的片段未必是当前显示的片段。
 */
case class SegmentNodeChangedEvent(segment: Segment[?])
