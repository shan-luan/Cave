package com.lomekwi.cave.ui.editpanel;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.SingleFileChooserListener;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.ui.editpanel.detail.SegDetailView;
import com.lomekwi.cave.ui.editpanel.filetree.FileTree;

import org.jspecify.annotations.NonNull;

public class EditPanelFrame extends VisTable {
    private static EditPanelFrame INSTANCE;
    private final VisSplitPane mediaPoolAndFileTreeSplitPane;
    private final VisSplitPane previewAndTimelineSplitPane;
    private final VisSplitPane previewAndDetailSplitPane;
    private final SegDetailView detailPanel;
    private EditPanel editPanel;
    private EditPanelFrame() {
        VisTable treePanel = new VisTable();
        treePanel.add(FileTree.getINSTANCE()).grow().row();
        VisTextButton addDirBtn = new VisTextButton("+");
        addDirBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                FileChooser chooser = new FileChooser("选择要添加到文件树的目录", FileChooser.Mode.OPEN);
                chooser.setSelectionMode(FileChooser.SelectionMode.DIRECTORIES);
                chooser.setListener(new SingleFileChooserListener() {
                    @Override
                    protected void selected(FileHandle file) {
                        FileTree.getINSTANCE().addRootDirectory(file.file());
                    }
                });
                App.root.getStage().addActor(chooser);
            }
        });
        treePanel.add(addDirBtn).fillX();
        ScrollPane fileTreeScrollPane = new VisScrollPane(treePanel);
        mediaPoolAndFileTreeSplitPane = new VisSplitPane(null, fileTreeScrollPane, true);
        mediaPoolAndFileTreeSplitPane.setSplitAmount(0.33f);
        detailPanel = new SegDetailView();
        previewAndDetailSplitPane = new VisSplitPane(null, detailPanel, false);
        previewAndDetailSplitPane.setSplitAmount(0.75f);
        previewAndTimelineSplitPane = new VisSplitPane(previewAndDetailSplitPane, null, true);
        previewAndTimelineSplitPane.setSplitAmount(0.615f);
        VisSplitPane mainSplitPane = new VisSplitPane(mediaPoolAndFileTreeSplitPane, previewAndTimelineSplitPane, false);
        mainSplitPane.setSplitAmount(0.18f);
        add(mainSplitPane).grow();
    }
    public static EditPanelFrame getINSTANCE() {
        if (INSTANCE == null) {
            INSTANCE = new EditPanelFrame();
        }
        return INSTANCE;
    }
    public EditPanelFrame with(@NonNull EditPanel editPanel) {
        if (!editPanel.equals(this.editPanel)) {
            this.editPanel = editPanel;
            mediaPoolAndFileTreeSplitPane.setFirstWidget(new VisScrollPane(editPanel.res.fill()));
            previewAndDetailSplitPane.setFirstWidget(editPanel.previewArea);
            previewAndTimelineSplitPane.setSecondWidget(editPanel.tl);
            editPanel.project.projEventBus.register(detailPanel);
        }
        return this;
    }

    public SegDetailView getDetailPanel() {
        return detailPanel;
    }

    @Override
    public float getMinHeight() {
        return 0;
    }
    @Override
    public float getMinWidth() {
        return 0;
    }
    @Override
    public float getPrefHeight() {
        return 0;
    }
    @Override
    public float getPrefWidth() {
        return 0;
    }
}
