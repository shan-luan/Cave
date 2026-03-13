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
    PreviewArea previewArea;
    Container<TlActor> tl;
    public EditPanel(Project project) {
        super();
        previewArea=new PreviewArea(new TlPrevCont(project,1920,1080));
        tl=new Container<>(new TlActor(project)).fill().clip();
        VisSplitPane sp = new VisSplitPane(previewArea,tl,true);
        add(sp).expand().fill();
    }
    public void dispose() {
        previewArea.dispose();
        tl.getActor().dispose();
    }
}
