package com.lomekwi.cave.ui.editpanel.settings;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;

public class SettingsTable extends VisTable {
    private static SettingsTable INSTANCE;
    private SettingsTable() {
        add(new VisLabel("todo"));
    }
    public static SettingsTable getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SettingsTable();
        }
        return INSTANCE;
    }
}
