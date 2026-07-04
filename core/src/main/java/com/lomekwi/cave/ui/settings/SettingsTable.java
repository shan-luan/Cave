package com.lomekwi.cave.ui.settings;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;

public class SettingsTable extends VisTable {
    private final VisSplitPane sp;

    public SettingsTable() {
        VisLabel l = new VisLabel(i18n("选择一个条目"));
        EntryTree tree = new EntryTree(this);
        tree.add(new EntryNode(i18n("快捷键"), ShortcutKeysTable::new));
        sp = new VisSplitPane(tree, l, false);
        l.setAlignment(1);
        add(sp).grow();
        sp.setSplitAmount(0.2f);
    }

    public void onEntrySelected(EntryTable entryTable) {
        sp.setSecondWidget(entryTable);
    }
}
