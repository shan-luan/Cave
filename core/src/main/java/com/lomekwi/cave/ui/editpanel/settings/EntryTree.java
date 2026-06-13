package com.lomekwi.cave.ui.editpanel.settings;

import com.kotcrab.vis.ui.widget.VisTree;
import com.lomekwi.cave.ui.listeners.ChangeListenerX;

public class EntryTree extends VisTree<EntryNode, EntryTable> {
    public EntryTree(SettingsTable settingsTable) {
        addListener(new ChangeListenerX(() -> {
            if (!getSelection().isEmpty()) {
                settingsTable.onEntrySelected(getSelection().first().getValue());
            }
        }));
    }
}
