package com.lomekwi.cave.ui.editpanel;

import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.util.Vars;
import com.lomekwi.cave.ui.editpanel.previewarea.PreviewArea;
import com.lomekwi.cave.ui.editpanel.previewarea.TlPrevCont;
import com.lomekwi.cave.ui.editpanel.tlarea.TlActor;

public class EditPanel extends VisTable {
    public EditPanel(Project project) {
        super();
        PreviewArea p=new PreviewArea(new TlPrevCont(project,1920,1080));
        Container<TlActor> tl=new Container<>(new TlActor(project.timeline,project.playhead)).fill().clip();
        VisSplitPane sp = new VisSplitPane(p,tl,true);
        add(sp).expand().fill();
    }
}
