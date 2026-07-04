package com.lomekwi.cave.ui.editpanel;

import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.ui.editpanel.previewarea.PreviewArea;
import com.lomekwi.cave.ui.editpanel.mediapool.MediaPool;
import com.lomekwi.cave.ui.editpanel.tlarea.TlGroup;
import com.lomekwi.cave.ui.editpanel.tlarea.TlRuler;
import com.kotcrab.vis.ui.widget.VisTable;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class EditPanel {
    final Project project;
    final PreviewArea previewArea;
    final VisTable tl;
    final Container<TlGroup> tlMain;
    final Container<MediaPool> res;

    public EditPanel(Project project) {
        super();
        this.project = project;
        previewArea = new PreviewArea(project);
        tlMain = new Container<>(new TlGroup(project)).fill().clip().minSize(0, 0);
        tl = new VisTable();
        tl.add(new TlRuler(tlMain.getActor())).fillX().expandX().row();
        tl.add(tlMain).grow();
        res = new Container<>(new MediaPool(project.resources, project.projEventBus));
    }

    public PreviewArea getPreviewArea() {
        return previewArea;
    }

    public TlGroup getTlGroup() {
        return tlMain.getActor();
    }

    public void dispose() {
        previewArea.dispose();
        tlMain.getActor().dispose();
    }
}
