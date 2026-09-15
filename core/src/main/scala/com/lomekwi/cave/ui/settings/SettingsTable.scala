package com.lomekwi.cave.ui.settings

import com.lomekwi.cave.util.i18n.I18N.i18n

import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisSplitPane
import com.kotcrab.vis.ui.widget.VisTable
import scala.compiletime.uninitialized

class SettingsTable extends VisTable {
  private var sp: VisSplitPane = uninitialized

  {
    val l = new VisLabel(i18n("选择一个条目"))
    val tree = new EntryTree(this)
    tree.add(new EntryNode(i18n("快捷键"), () => new ShortcutKeysTable()))
    sp = new VisSplitPane(tree, l, false)
    l.setAlignment(1)
    add(sp).grow()
    sp.setSplitAmount(0.2f)
  }

  def onEntrySelected(entryTable: EntryTable): Unit = {
    sp.setSecondWidget(entryTable)
  }
}
