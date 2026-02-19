package com.lomekwi.cine.ui.editpanel;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cine.ui.editpanel.previewarea.PreviewArea;

public class EditPanel extends VisTable {
    public EditPanel() {
        super();
        add(new PreviewArea(1920,1080));
    }
}
