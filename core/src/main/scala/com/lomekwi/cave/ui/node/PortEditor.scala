package com.lomekwi.cave.ui.node

import com.badlogic.gdx.scenes.scene2d.Actor
import com.lomekwi.cave.pipeline.Node

trait PortEditor extends PortHolder {
  override def port: Node.InPort[?]

  /** 端口名标签。 */
  def label: Actor

  /** 编辑默认值的控件。 */
  def control: Actor
}
