package com.lomekwi.cave.ui.tabs;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.ui.editpanel.EditPanel;
import com.lomekwi.cave.ui.editpanel.EditPanelFrame;

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
        return EditPanelFrame.getINSTANCE().with(editPanel);
    }

    public EditPanel getEditPanel() {
        return editPanel;
    }

    public Project getProject() {
        return project;
    }
    //不需要手动调用！
    @Override
    public void dispose() {
        super.dispose();
        project.close();
        editPanel.dispose();
    }
}
