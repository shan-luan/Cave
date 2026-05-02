package com.lomekwi.cave.ui.tabs;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.lomekwi.cave.ui.editpanel.settings.SettingsTable;

public class SettingsTab extends Tab {
    @Override
    public String getTabTitle() {
        return i18n("设置");
    }

    @Override
    public Table getContentTable() {
        return SettingsTable.getInstance();
    }
}
