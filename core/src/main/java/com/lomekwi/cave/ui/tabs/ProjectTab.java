package com.lomekwi.cave.ui.tabs;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.lomekwi.cave.ui.editpanel.EditPanel;

public class ProjectTab extends Tab {
    private final EditPanel editPanel;
    public ProjectTab(){
        super(true, true);
        editPanel = new EditPanel();
    }
    @Override
    public String getTabTitle() {
        return "new Project";
    }
    @Override
    public Table getContentTable() {
        return editPanel;
    }
}
