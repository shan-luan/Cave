package com.lomekwi.cave.ui.editpanel;

import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.GlobalVars;
import com.lomekwi.cave.ui.editpanel.previewarea.PreviewArea;
import com.lomekwi.cave.ui.editpanel.previewarea.TlPrevCont;
import com.lomekwi.cave.ui.editpanel.tlarea.TlActor;
import com.lomekwi.cave.ui.editpanel.tlarea.TlContainer;

public class EditPanel extends VisTable {
    public EditPanel() {
        super();
        PreviewArea p=new PreviewArea(new TlPrevCont(1920,1080));
        TlContainer tl=new TlContainer(new TlActor(GlobalVars.getProject().timeline,GlobalVars.getProject().playhead));
        VisSplitPane sp = new VisSplitPane(p,tl,true);
        add(sp).expand().fill();
    }
}
