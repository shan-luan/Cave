package com.lomekwi.cave.ui.tabs;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.google.common.eventbus.Subscribe;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.project.ProjectDirtyChangedEvent;
import com.lomekwi.cave.project.Projects;
import com.lomekwi.cave.ui.editpanel.EditPanel;
import com.lomekwi.cave.ui.editpanel.EditPanelFrame;
import games.spooky.gdx.nativefilechooser.NativeFileChooserCallback;
import games.spooky.gdx.nativefilechooser.NativeFileChooserConfiguration;
import games.spooky.gdx.nativefilechooser.NativeFileChooserIntent;

import java.io.IOException;

public class ProjectTab extends Tab {
    private final EditPanel editPanel;
    private final Project project;
    public ProjectTab(Project project){
        super(true, true);
        editPanel = new EditPanel(project);
        this.project=project;
        project.projEventBus.register(this);
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

    @Subscribe
    public void onProjectDirtyChanged(ProjectDirtyChangedEvent event) {
        setDirty(project.isDirty());
    }

    //FIXME：当保存时，如果取消文件选择器，则会在未保存的情况下关闭标签页。这是vis-ui的设计缺陷且我已经打开一个issue(#405)
    @Override
    public boolean save() {
        if (project.getSavePath() == null) {
            NativeFileChooserConfiguration conf = new NativeFileChooserConfiguration();
            conf.title = i18n("选择保存位置...");
            if (Gdx.app.getType() == Application.ApplicationType.Android) {
                conf.mimeFilter = "*/*";
            }
            conf.intent = NativeFileChooserIntent.SAVE;
            App.fileChooser.chooseFile(conf, new NativeFileChooserCallback() {
                @Override
                public void onFileChosen(com.badlogic.gdx.files.FileHandle file) {
                    try {
                        Projects.save(project, file);
                        setDirty(false);
                    } catch (IOException e) {
                        Gdx.app.error("ProjectTab", "保存项目失败", e);
                    }
                }
                @Override
                public void onCancellation() {}
                @Override
                public void onError(Exception exception) {
                    Gdx.app.error("ProjectTab", "保存项目失败", exception);
                }
            });
            return true;
        }
        try {
            Projects.save(project);
            setDirty(false);
            return true;
        } catch (IOException e) {
            Gdx.app.error("ProjectTab", "保存项目失败", e);
            return false;
        }
    }

    //不需要手动调用！
    @Override
    public void dispose() {
        project.projEventBus.unregister(this);
        super.dispose();
        project.close();
        editPanel.dispose();
    }
}
