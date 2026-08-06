package com.lomekwi.cave.ui.tabs;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.lomekwi.cave.ui.nodeeditor.NodeEditorView;
import com.lomekwi.cave.util.i18n.I18N;

public class NodeEditorTab extends Tab {

    public NodeEditorTab() {
        super(false, true);
    }

    @Override
    public String getTabTitle() {
        return I18N.i18n("节点编辑器");
    }

    @Override
    public Table getContentTable() {
        return new NodeEditorView();
    }
}
