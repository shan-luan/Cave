package com.lomekwi.cave.ui.settings

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

import com.kotcrab.vis.ui.widget.VisDialog
import com.lomekwi.cave.app.App

class SettingsDialog extends VisDialog(("设置".in(Zh) | "Settings".in(En)).str()) {

  {
    addCloseButton()

    getContentTable.add(new SettingsTable()).grow()

    App.root.showDialog(this)
    setSize(800, 600)
    centerWindow()
  }
}
