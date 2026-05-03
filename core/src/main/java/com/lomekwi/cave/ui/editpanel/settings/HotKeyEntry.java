package com.lomekwi.cave.ui.editpanel.settings;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.kotcrab.vis.ui.widget.VisLabel;

public class HotKeyEntry extends EntryTable{
    public HotKeyEntry() {
        super();
        add(new VisLabel(i18n("快捷键"))).grow();
    }
    @Override
    public String getName() {
        return i18n("快捷键");
    }
}
