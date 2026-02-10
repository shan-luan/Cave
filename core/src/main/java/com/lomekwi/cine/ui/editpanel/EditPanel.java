package com.lomekwi.cine.ui.editpanel;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;

public class EditPanel extends VisTable {
    public EditPanel() {
        super();
        add(new VisLabel(Double.toString(Math.random())));
    }
}
