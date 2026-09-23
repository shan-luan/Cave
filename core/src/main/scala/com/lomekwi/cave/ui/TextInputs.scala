package com.lomekwi.cave.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.kotcrab.vis.ui.widget.VisTextField

object TextInputs {
  /**
   * actor 自身或其祖先中是否有文本输入控件。注意 VisUI 的 {@link VisTextField} 不继承
   * {@link TextField}，两种都要判。
   */
  def contains(actor: Actor): Boolean = {
    var current: Actor = actor
    while (current != null) {
      if (current.isInstanceOf[TextField] || current.isInstanceOf[VisTextField]) {
        return true
      }
      current = current.getParent
    }
    false
  }
}
