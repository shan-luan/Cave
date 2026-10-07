package com.lomekwi.cave.ui.node

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextArea
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Segment
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.playback.RefreshRequestEvent

import java.util.Objects

/**
 * [[String]] 输入端口编辑 widget，[[VisTextArea]] 行。直接持有端口模型，修改写默认值并刷新预览。
 */
final class TextPortEditor(port0: Node.InPort[?], segment: Segment) extends VisTable with PortEditor {
  override final val port: Node.InPort[?] = port0
  override final val label: VisLabel = new VisLabel(port.name)
  private final val textArea: VisTextArea = {
    val defaultValue: String = port.defaultData.asInstanceOf[String]
    val area: VisTextArea = new VisTextArea(if (defaultValue == null) "" else defaultValue)
    // 撑高 prefHeight，否则 X2 皮肤下 linesShowing 为 0，文字不会绘制
    area.setPrefRows(3f)
    area.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        val newVal: String = area.getText
        val oldVal: String = port.defaultData.asInstanceOf[String]
        if (!Objects.equals(oldVal, newVal)) {
          val strPort: Node.InPort[String] = port.asInstanceOf[Node.InPort[String]]
          strPort.defaultData = newVal
          val p: Project = App.root.getFrontendProject
          if (p != null) {
            p.projEventBus.post(RefreshRequestEvent)
          }
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
}
