package com.lomekwi.cave.ui.node

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel
import com.kotcrab.vis.ui.widget.spinner.Spinner
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.pipeline.Source
import com.lomekwi.cave.pipeline.num.NumFrame
import com.lomekwi.cave.project.Project
import com.lomekwi.cave.timeline.UndoManager
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent
import java.util

/**
 * NumFrame 输入端口编辑 widget：Spinner 行。直接持有端口模型，修改写默认值记 undo，
 * 并在自身的 act() 中把模型值回显到 widget（undo、gizmo 等外部修改后同步）。
 */
final class NumPortEditor(port0: Node.InPort[?], source: Source[?]) extends VisTable with PortEditor {
  private final val port: Node.InPort[?] = port0
  private final val defaultData: NumFrame = port.getDefaultData.asInstanceOf[NumFrame]
  private final val model: SimpleFloatSpinnerModel = new SimpleFloatSpinnerModel(
    (if (defaultData != null) defaultData.getVal else 0.0).toFloat, -99999f, 99999f, 1f, 2)
  private final val spinner: Spinner = new Spinner("", model)
  spinner.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
      val newVal: Double = model.getValue.toDouble
      val oldVal: Double = if (defaultData != null) defaultData.getVal else 0.0
      if (oldVal == newVal) return
      val p: Project = App.root.getFrontendProject
      if (p != null) {
        p.undoManager.record(UndoManager.NumPortValueCommand(port, source, oldVal, newVal))
        p.projEventBus.post(RefreshRequestEvent)
      }
      if (defaultData != null) defaultData.setVal(newVal)
    }
  })
  align(Align.topLeft)
  defaults().left()
  add(new VisLabel(port.getName)).pad(2f)
  add(spinner).width(90f).pad(2f)

  override def getPort: Node.InPort[?] = port

  override def act(delta: Float): Unit = {
    super.act(delta)
    // 用户正在输入时不覆盖，避免打断编辑
    if (spinner.getTextField.hasKeyboardFocus) return
    val data: NumFrame = port.getDefaultData.asInstanceOf[NumFrame]
    val modelVal: Double = if (data != null) data.getVal else 0.0
    val current: Float = model.getValue
    if (Math.abs(current - modelVal) > 0.005f) {
      model.setValue(modelVal.toFloat, false)
      spinner.getTextField.setText(
        String.format(util.Locale.US, "%.2f", modelVal))
    }
  }
}
