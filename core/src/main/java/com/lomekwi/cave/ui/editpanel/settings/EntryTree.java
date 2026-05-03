package com.lomekwi.cave.ui.editpanel.settings;

import com.kotcrab.vis.ui.widget.VisTree;
import com.lomekwi.cave.ui.listeners.ChangeListenerX;

public class EntryTree extends VisTree<EntryNode,EntryTable> {
    public EntryTree() {
        addListener(new ChangeListenerX(()->{
            SettingsTable.getInstance().onEntrySelected(getSelection().first().getValue());
        }));

        add(new EntryNode(new HotKeyEntry()));
    }
}
