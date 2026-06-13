package com.lomekwi.cave.ui.editpanel.settings;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.kotcrab.vis.ui.widget.VisLabel;

public class ShortcutKeysTable extends EntryTable {

    public ShortcutKeysTable() {
        add(new VisLabel(i18n("TODO")));
    }

    @Override
    public String getName() {
        return i18n("快捷键");
    }
}
