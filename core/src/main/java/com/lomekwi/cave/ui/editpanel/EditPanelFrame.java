package com.lomekwi.cave.ui.editpanel;

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.lomekwi.cave.ui.editpanel.filetree.FileTree;

import org.jspecify.annotations.NonNull;

public class EditPanelFrame extends VisTable {
    private static EditPanelFrame INSTANCE;
    private final VisSplitPane fileTreeAndMainSplitPane;
    private final VisSplitPane mainAndTimelineSplitPane;
    private final VisSplitPane resourceAndPreviewSplitPane;
    private EditPanel editPanel;
    private EditPanelFrame() {
        resourceAndPreviewSplitPane = new VisSplitPane(null,null,false);
        mainAndTimelineSplitPane = new VisSplitPane( resourceAndPreviewSplitPane,null,true);
        ScrollPane fileTreeScrollPane = new VisScrollPane(FileTree.getINSTANCE());
        fileTreeAndMainSplitPane = new VisSplitPane(fileTreeScrollPane,mainAndTimelineSplitPane,false);
        fileTreeAndMainSplitPane.setSplitAmount(0.18f);
        add(fileTreeAndMainSplitPane).grow();
    }
    public static EditPanelFrame getINSTANCE() {
        if (INSTANCE == null) {
            INSTANCE = new EditPanelFrame();
        }
        return INSTANCE;
    }
    public EditPanelFrame with(@NonNull EditPanel editPanel) {
        if(!editPanel.equals(this.editPanel)){
            this.editPanel = editPanel;
            resourceAndPreviewSplitPane.setFirstWidget(editPanel.res.fill());
            resourceAndPreviewSplitPane.setSecondWidget(editPanel.previewArea);
            mainAndTimelineSplitPane.setSecondWidget(editPanel.tl.fill().clip().minSize(0,0));
        }
        return this;
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
