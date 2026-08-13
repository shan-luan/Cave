package com.lomekwi.cave.ui.tabs;

import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.lomekwi.cave.ui.editpanel.tlarea.TlGroup;
import com.lomekwi.cave.ui.editpanel.tlarea.TlRuler;
import com.lomekwi.cave.util.i18n.I18N;

public class TimelineTab extends Tab {
    private final Table content;

    public TimelineTab(Container<TlGroup> tlMain) {
        super(false, false);
        content = new VisTable();
        content.add(new TlRuler(tlMain.getActor())).growX().row();
        content.add(tlMain).grow();
    }

    @Override
    public String getTabTitle() {
        return I18N.i18n("时间线");
    }

    @Override
    public Table getContentTable() {
        return content;
    }
}
