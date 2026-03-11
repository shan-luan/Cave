package com.lomekwi.cave.ui.editpanel;

import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.util.GlobalVars;
import com.lomekwi.cave.ui.editpanel.previewarea.PreviewArea;
import com.lomekwi.cave.ui.editpanel.previewarea.TlPrevCont;
import com.lomekwi.cave.ui.editpanel.tlarea.TlActor;

public class EditPanel extends VisTable {
    public EditPanel() {
        super();
        PreviewArea p=new PreviewArea(new TlPrevCont(1920,1080));
        Container<TlActor> tl=new Container<>(new TlActor(GlobalVars.project.timeline,GlobalVars.project.playhead)).fill().clip();
        VisSplitPane sp = new VisSplitPane(p,tl,true);
        add(sp).expand().fill();
    }
}
