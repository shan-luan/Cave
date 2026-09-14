package com.lomekwi.cave.ui.settings

import com.kotcrab.vis.ui.widget.VisTree
import com.lomekwi.cave.ui.listeners.ChangeListenerX

class EntryTree(settingsTable: SettingsTable) extends VisTree[EntryNode, EntryTable] {
  addListener(new ChangeListenerX(() => {
    if (!getSelection.isEmpty) {
      settingsTable.onEntrySelected(getSelection.first().getValue)
    }
  }))
}
