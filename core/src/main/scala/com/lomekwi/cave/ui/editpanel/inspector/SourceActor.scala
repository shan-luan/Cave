package com.lomekwi.cave.ui.editpanel.inspector

import com.badlogic.gdx.utils.Align
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.ui.widget.Card

import scala.jdk.CollectionConverters.*

/**
 * 通用源信息卡：显示源名称、输入端口与信息输出端口（不含参与 filter 链的 FilterOut）。
 * 类型 → widget 的映射由 {@link CardWidgetsRegistry} 维护。
 */
final class SourceActor(private val source: Source[?]) extends Card(source.getDisplayName) {
  align(Align.top | Align.left)
  defaults().left()
  for (in <- source.getInPorts.asScala) {
    val widget = CardWidgetsRegistry.createEditor(in, source)
    // 未注册该端口类型的 widget，不显示
    if (widget != null) {
      add(widget).growX().pad(2).row()
    }
  }
  for (out <- source.getOutPorts.asScala) {
    val row = CardWidgetsRegistry.createOutputRow(out)
    // FilterOut 与未知类型的输出端口不显示
    if (row != null) {
      add(row).pad(2).left().row()
    }
  }
}
