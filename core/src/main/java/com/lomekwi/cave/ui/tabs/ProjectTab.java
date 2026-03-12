package com.lomekwi.cave.ui.tabs;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.ui.editpanel.EditPanel;

public class ProjectTab extends Tab {
    private final EditPanel editPanel;
    private final Project project;
    public ProjectTab(Project project){
        super(true, true);
        editPanel = new EditPanel(project);
        this.project=project;
    }
    @Override
    public String getTabTitle() {
        return project.name;
    }
    @Override
    public Table getContentTable() {
        return editPanel;
    }

    public Project getProject() {
        return project;
    }
}
