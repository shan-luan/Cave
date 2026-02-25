package com.lomekwi.cave.ui.editpanel;

import com.badlogic.gdx.Gdx;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.ui.editpanel.previewarea.PreviewArea;
import com.lomekwi.cave.ui.editpanel.previewarea.TlPrevCont;

public class EditPanel extends VisTable {
    public EditPanel() {
        super();
        PreviewArea p=new PreviewArea(new TlPrevCont(1920,1080));
        add(p).size(1920,1080);
    }
}
