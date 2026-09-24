package com.lomekwi.cave.app.selection

import com.lomekwi.cave.pipeline.Source

/**
 * 某个源的节点图（滤镜链、端口值、变换节点）被改动，界面按需重建。
 * 与选中无关，改动的源未必是当前显示的源。
 */
case class SourceNodeChangedEvent(source: Source[?])
