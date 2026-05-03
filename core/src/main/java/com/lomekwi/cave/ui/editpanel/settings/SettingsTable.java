package com.lomekwi.cave.ui.editpanel.settings;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;

public class SettingsTable extends VisTable {
    private static SettingsTable INSTANCE;
    private final VisSplitPane sp;
    private SettingsTable() {
        VisLabel l = new VisLabel(i18n("选择一个条目"));
        sp = new VisSplitPane(new EntryTree(),l, false);
        l.setAlignment(1);
        add(sp).grow();
        sp.setSplitAmount(0.2f);
    }
    public static SettingsTable getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SettingsTable();
        }
        return INSTANCE;
    }
    public void onEntrySelected(EntryTable entryTable) {
        sp.setSecondWidget(entryTable);
    }
}
