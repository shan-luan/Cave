package com.lomekwi.cave.ui.tabs.edit

import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import com.lomekwi.cave.ui.editpanel.tlarea.TlGroup
import com.lomekwi.cave.ui.editpanel.tlarea.TlRuler
import com.lomekwi.cave.util.i18n.I18N

class TimelineTab(tlMain: Container[TlGroup]) extends Tab(false, false) {
  private final val content: Table = new VisTable()

  {
    content.add(new TlRuler(tlMain.getActor)).growX().row()
    content.add(tlMain).grow()
  }

  override def getTabTitle(): String = {
    I18N.i18n("时间线")
  }

  override def getContentTable(): Table = {
    content
  }
}
