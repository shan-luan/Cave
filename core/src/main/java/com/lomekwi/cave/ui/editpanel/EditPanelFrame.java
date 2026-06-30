package com.lomekwi.cave.ui.editpanel;

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.lomekwi.cave.ui.editpanel.filetree.FileTree;

import org.jspecify.annotations.NonNull;

public class EditPanelFrame extends VisTable {
    private static EditPanelFrame INSTANCE;
    private final VisSplitPane mediaPoolAndFileTreeSplitPane;
    private final VisSplitPane previewAndTimelineSplitPane;
    private final VisSplitPane previewAndDetailSplitPane;
    private final VisTable detailPlaceholder;
    private EditPanel editPanel;
    private EditPanelFrame() {
        ScrollPane fileTreeScrollPane = new VisScrollPane(FileTree.getINSTANCE());
        mediaPoolAndFileTreeSplitPane = new VisSplitPane(null, fileTreeScrollPane, true);
        mediaPoolAndFileTreeSplitPane.setSplitAmount(0.33f);
        detailPlaceholder = new VisTable();
        previewAndDetailSplitPane = new VisSplitPane(null, detailPlaceholder, false);
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
        }
        return this;
    }

    public VisTable getDetailPanel() {
        return detailPlaceholder;
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
