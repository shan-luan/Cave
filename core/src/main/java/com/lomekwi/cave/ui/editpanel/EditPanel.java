package com.lomekwi.cave.ui.editpanel;

import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.ui.editpanel.previewarea.PreviewArea;
import com.lomekwi.cave.ui.editpanel.previewarea.TlPrevCont;
import com.lomekwi.cave.ui.editpanel.mediapool.MediaPool;
import com.lomekwi.cave.ui.editpanel.tlarea.TlActor;

public class EditPanel {
    PreviewArea previewArea;
    Container<TlActor> tl;
    Container<MediaPool> res;
    public EditPanel(Project project) {
        super();
        previewArea=new PreviewArea(new TlPrevCont(project, 1920, 1080));
        tl=new Container<>(new TlActor(project));
        res=new Container<>(new MediaPool(project.resources));
    }
    public void dispose() {
        previewArea.dispose();
        tl.getActor().dispose();
    }
}
