package com.lomekwi.cave.ui.editpanel;

import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.ui.editpanel.filetree.FileTree;

import org.jspecify.annotations.NonNull;

public class EditPanelFrame extends VisTable {
    private static EditPanelFrame INSTANCE;
    private final VisSplitPane ftAndA;
    private final VisSplitPane A_BAndTl;
    private final VisSplitPane B_ResAndPrev;
    private EditPanel editPanel;
    public EditPanelFrame() {
        INSTANCE = this;
        B_ResAndPrev = new VisSplitPane(null,null,false);
        A_BAndTl = new VisSplitPane( B_ResAndPrev,null,true);
        ftAndA = new VisSplitPane(FileTree.getINSTANCE(),A_BAndTl,false);
        ftAndA.setSplitAmount(0.2f);
        add(ftAndA).fill().expand();
    }
    public static EditPanelFrame getINSTANCE() {
        return INSTANCE;
    }
    public EditPanelFrame with(@NonNull EditPanel editPanel) {
        if(!editPanel.equals(this.editPanel)){
            this.editPanel = editPanel;
            B_ResAndPrev.setFirstWidget(editPanel.res.fill());
            B_ResAndPrev.setSecondWidget(editPanel.previewArea);
            A_BAndTl.setSecondWidget(editPanel.tl.fill().clip().minSize(0,0));
        }
        return this;
    }
}
