package com.lomekwi.cave.ui.settings

import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisSelectBox
import com.lomekwi.cave.app.App
import com.lomekwi.cave.ui.listeners.ChangeListenerX
import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

class LanguageSettingsTable extends EntryTable {
  {
    top().left()

    add(new VisLabel(("语言设置".in(Zh) | "Language Settings".in(En)).str())).pad(10).left().row()

    val langs: Seq[Lang] = LanguageSettingsTable.langs
    val names: Array[String] = LanguageSettingsTable.names

    val box: VisSelectBox[String] = new VisSelectBox[String]()
    box.setItems(names*)
    box.setSelected(names(langs.indexOf(App.languageManager.persistedLang)))

    box.addListener(new ChangeListenerX(() => {
      val picked: Lang = langs(names.indexOf(box.getSelected))
      App.languageManager.persist(picked)
      App.root.toastManager.show(("重启后生效".in(Zh) | "Takes effect after restart".in(En)).str(), 2f)
    }))

    add(box).pad(5).width(240).left().row()
    add(new VisLabel(("切换语言后需要重启应用".in(Zh) | "Restart the application after switching language".in(En)).str()))
      .pad(10).left()
  }

  override def getName: String = {
    ("语言".in(Zh) | "Language".in(En)).str()
  }
}

object LanguageSettingsTable {
  final val langs: Seq[Lang] = Seq(Zh, En)
  final val names: Array[String] = Array("中文", "English")
}
