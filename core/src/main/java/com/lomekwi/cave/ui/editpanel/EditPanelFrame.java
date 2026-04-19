package com.lomekwi.cave.ui.editpanel;

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
    public EditPanelFrame() {
        INSTANCE = this;
        resourceAndPreviewSplitPane = new VisSplitPane(null,null,false);
        mainAndTimelineSplitPane = new VisSplitPane( resourceAndPreviewSplitPane,null,true);
        VisScrollPane fileTreeScrollPane = new VisScrollPane(FileTree.getINSTANCE());
        fileTreeAndMainSplitPane = new VisSplitPane(fileTreeScrollPane,mainAndTimelineSplitPane,false);
        fileTreeAndMainSplitPane.setSplitAmount(0.18f);
        add(fileTreeAndMainSplitPane).fill().expand();
    }
    public static EditPanelFrame getINSTANCE() {
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
}
