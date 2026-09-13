package com.lomekwi.cave.ui.listeners

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener

class ChangeListenerX(private val runnable: Runnable) extends ChangeListener {
  override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
    runnable.run()
  }
}
