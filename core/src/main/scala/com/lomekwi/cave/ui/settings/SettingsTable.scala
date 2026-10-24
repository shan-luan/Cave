package com.lomekwi.cave.ui.settings

import com.lomekwi.cave.util.i18n.*
import com.lomekwi.cave.util.i18n.BasicLang.*
import com.lomekwi.cave.util.i18n.TransTargetType.given

import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisSplitPane
import com.kotcrab.vis.ui.widget.VisTable
import scala.compiletime.uninitialized

class SettingsTable extends VisTable {
  private var sp: VisSplitPane = uninitialized

  {
    val l = new VisLabel(("选择一个条目".in(Zh) | "Select an entry".in(En)).str())
    val tree = new EntryTree(this)
    tree.add(new EntryNode(("语言".in(Zh) | "Language".in(En)).str(), () => new LanguageSettingsTable()))
    tree.add(new EntryNode(("快捷键".in(Zh) | "Shortcut".in(En)).str(), () => new ShortcutKeysTable()))
    sp = new VisSplitPane(tree, l, false)
    l.setAlignment(1)
    add(sp).grow()
    sp.setSplitAmount(0.2f)
  }

  def onEntrySelected(entryTable: EntryTable): Unit = {
    sp.setSecondWidget(entryTable)
  }
}
