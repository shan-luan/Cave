package com.lomekwi.cave.ui.node

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.kotcrab.vis.ui.widget.{VisLabel, VisTable}
import com.kotcrab.vis.ui.widget.spinner.{SimpleFloatSpinnerModel, Spinner}
import com.lomekwi.cave.app.App
import com.lomekwi.cave.pipeline.Node
import com.lomekwi.cave.playback.RefreshRequestEvent
import com.lomekwi.cave.timeline.Segment

import java.util

/**
 * 数值输入端口编辑 widget，[[Spinner]] 行。直接持有端口模型，修改写默认值记 undo，
 * 并在自身的 [[act]] 中把模型值回显到 widget（undo、gizmo 等外部修改后同步）。
 */
final class NumPortEditor(port0: Node.InPort[?], segment: Segment) extends VisTable with PortEditor {
  override val port: Node.InPort[?] = port0
  override val label: VisLabel = new VisLabel(port.name)
  private val model: SimpleFloatSpinnerModel = new SimpleFloatSpinnerModel(
    defaultValue.toFloat, -99999f, 99999f, 1f, 2)
  private val spinner: Spinner = new Spinner("", model)
  spinner.addListener(new ChangeListener {
    override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
      val newVal: Double = model.getValue.toDouble
      val oldVal: Double = defaultValue
      if (oldVal != newVal) {
        val c = App.root.getFrontendContext
        setDefaultValue(newVal)
        if (c != null) {
          c.touch()
          c.bus.post(RefreshRequestEvent)
        }
      }
    }
  })
  align(Align.topLeft)
  defaults().left()
  add(label).pad(2f)
  add(spinner).width(90f).pad(2f)

  override def control: Actor = spinner

  override def act(delta: Float): Unit = {
    super.act(delta)
    // 用户正在输入时不覆盖，避免打断编辑
    if (!spinner.getTextField.hasKeyboardFocus) {
      val modelVal: Double = defaultValue
      val current: Float = model.getValue
      if (Math.abs(current - modelVal) > 0.005f) {
        model.setValue(modelVal.toFloat, false)
        spinner.getTextField.setText(
          String.format(util.Locale.US, "%.2f", modelVal))
      }
    }
  }

  private def defaultValue: Double = {
    port.defaultData.asInstanceOf[Double]
  }

  private def setDefaultValue(v: Double): Unit = {
    port.asInstanceOf[Node.InPort[Double]].defaultData = v
  }
}
