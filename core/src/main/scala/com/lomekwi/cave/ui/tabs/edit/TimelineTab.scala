package com.lomekwi.cave.ui.tabs.edit

import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import com.lomekwi.cave.ui.editpanel.tlarea.TimelineView
import com.lomekwi.cave.ui.editpanel.tlarea.TlRuler
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

class TimelineTab(tlMain: Container[TimelineView]) extends Tab(false, false) {
  private final val content: Table = new VisTable()

  {
    content.add(new TlRuler(tlMain.getActor)).growX().row()
    content.add(tlMain).grow()
  }

  override def getTabTitle: String = {
    ("时间线".in(Zh) | "Timeline".in(En)).str()
  }

  override def getContentTable: Table = {
    content
  }
}
