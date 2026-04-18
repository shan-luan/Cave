package com.lomekwi.cave.ui.editpanel;

import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.ui.editpanel.previewarea.PreviewArea;
import com.lomekwi.cave.ui.editpanel.mediapool.MediaPool;
import com.lomekwi.cave.ui.editpanel.tlarea.TlGroup;

public class EditPanel {
    PreviewArea previewArea;
    Container<TlGroup> tl;
    Container<MediaPool> res;
    public EditPanel(Project project) {
        super();
        previewArea=new PreviewArea(project);
        tl=new Container<>(new TlGroup(project));
        res=new Container<>(new MediaPool(project.resources));
    }
    public void dispose() {
        previewArea.dispose();
        tl.getActor().dispose();
    }
}
