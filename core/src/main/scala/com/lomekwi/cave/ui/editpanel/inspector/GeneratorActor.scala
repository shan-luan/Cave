package com.lomekwi.cave.ui.editpanel.inspector

import com.badlogic.gdx.utils.Align
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.{Generator, Source}
import com.lomekwi.cave.ui.widget.Card

import scala.jdk.CollectionConverters.*

/**
 * 生成器信息卡，显示生成器名称与输入端口、信息输出端口（不含参与 filter 链的 FilterOut）。
 * 一个源的信息由这张卡与紧随其后的各 {@link FilterActor} 共同呈现。
 * 类型 → widget 的映射由 {@link CardWidgetsRegistry} 维护。
 */
final class GeneratorActor(private val source: Source[?]) extends Card(source.getDisplayName) {
  private final val generator: Generator[?] = source.getGenerator

  align(Align.top | Align.left)
  defaults().left()
  for (in <- generator.getInPorts.asScala) {
    val widget = App.cardWidgetsRegistry.createEditor(in, source)
    // 未注册该端口类型的 widget，不显示
    if (widget != null) {
      add(widget).growX().pad(2).row()
    }
  }
  for (out <- generator.getOutPorts.asScala) {
    val row = App.cardWidgetsRegistry.createOutputRow(out)
    // FilterOut 与未知类型的输出端口不显示
    if (row != null) {
      add(row).pad(2).left().row()
    }
  }
}
