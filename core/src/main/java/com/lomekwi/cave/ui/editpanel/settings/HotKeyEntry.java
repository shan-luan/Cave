package com.lomekwi.cave.ui.editpanel.settings;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.lomekwi.cave.app.HotkeyManager;

import java.util.Map;
import java.util.Set;

public class HotKeyEntry extends EntryTable{
    public HotKeyEntry() {
        super();
        Set<Map.Entry<HotkeyManager.KeyCombo,String>> s = HotkeyManager.getInstance().getAllHotkeyNames().entrySet();
        for (Map.Entry<HotkeyManager.KeyCombo,String> entry : s) {
            add(new VisLabel(entry.getKey().toString())).left();
            add(new VisLabel(entry.getValue())).left();
            row();
        }
    }
    @Override
    public String getName() {
        return i18n("快捷键");
    }
}
