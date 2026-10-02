package com.lomekwi.cave.ui.settings

import com.lomekwi.cave.util.i18n.I18N.i18n

import com.kotcrab.vis.ui.widget.VisDialog
import com.lomekwi.cave.app.App

class SettingsDialog extends VisDialog(i18n("设置")) {

  {
    addCloseButton()

    getContentTable.add(new SettingsTable()).grow()

    App.root.showDialog(this)
    setSize(800, 600)
    centerWindow()
  }
}
