package com.lomekwi.cave.ui.node

import com.badlogic.gdx.scenes.scene2d.Actor
import com.lomekwi.cave.pipeline.Node

trait PortEditor extends PortHolder {
  override def port: Node.InPort[?]

  /** 端口名标签，始终绘制。 */
  def label: Actor

  /** 编辑默认值的控件，端口连接后由 [[InPortActor]] 隐藏。 */
  def control: Actor
}
