package com.lomekwi.cave.ui.node

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextArea
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.timeline.Segment
import com.lomekwi.cave.playback.RefreshRequestEvent

import java.util.Objects

/**
 * [[String]] 输入端口编辑 widget，[[VisTextArea]] 行。模型值保存在所属容器里，修改回写容器并刷新预览。
 */
final class TextPortEditor(port0: Node.InPort[?], segment: Segment) extends VisTable with PortEditor {
  override val port: Node.InPort[?] = port0
  override val label: VisLabel = new VisLabel(port.name)
  private val textArea: VisTextArea = {
    val defaultValue: String = currentDefault
    val area: VisTextArea = new VisTextArea(if (defaultValue == null) "" else defaultValue)
    // 撑高 prefHeight，否则 X2 皮肤下 linesShowing 为 0，文字不会绘制
    area.setPrefRows(3f)
    area.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        val newVal: String = area.getText
        val oldVal: String = currentDefault
        if (!Objects.equals(oldVal, newVal)) {
          setDefault(newVal)
        }
      }
    })
    area
  }
  {
    align(Align.topLeft)
    defaults().left()
    add(label).pad(2f).left().row()
    add(textArea).growX().pad(2f)
  }

  override def control: Actor = textArea

  private def currentSegment: Segment = {
    if (segment == null) {
      null
    } else {
      val c = App.root.getFrontendSession
      if (c == null) segment else c.timeline.segment(segment.id).orNull
    }
  }

  private def editorView: NodeEditorView = {
    var current: Actor = getParent
    while (current != null) {
      current match {
        case view: NodeEditorView => return view
        case _ => current = current.getParent
      }
    }
    null
  }

  private def currentDefault: String = {
    val current = currentSegment
    if (current != null) {
      current.defaultOf(port).asInstanceOf[String]
    } else {
      val view = editorView
      if (view == null) port.defaultData.asInstanceOf[String] else view.defaultOf(port).asInstanceOf[String]
    }
  }

  private def setDefault(v: String): Unit = {
    val current = currentSegment
    val c = App.root.getFrontendSession
    if (current != null) {
      if (c != null) {
        c.updateSegment(current.id)(_.setFilterDefault(port, v))
        c.bus.post(RefreshRequestEvent)
      }
    } else {
      val view = editorView
      if (view != null) {
        view.setDefault(port, v)
      }
      if (c != null) {
        c.bus.post(RefreshRequestEvent)
      }
    }
  }
}
