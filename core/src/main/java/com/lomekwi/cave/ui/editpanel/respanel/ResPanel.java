package com.lomekwi.cave.ui.editpanel.respanel;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.layout.FlowGroup;
import com.kotcrab.vis.ui.layout.GridGroup;
import com.kotcrab.vis.ui.layout.HorizontalFlowGroup;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisImageTextButton;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.media.MedRes;
import com.lomekwi.cave.resource.media.Media;
import com.lomekwi.cave.ui.Root;

import java.io.File;
import java.util.Map;
import java.util.Set;


public class ResPanel extends FlowGroup {
    private final TextureRegionDrawable test;
    public ResPanel(Map<File, Resource> resources){
        super(false);
        test = new TextureRegionDrawable(new Texture("libgdx.png"));
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        Root.getInstance().getDragAndDrop().addTarget(new DragAndDrop.Target(this) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                return payload.getObject() instanceof File;
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                File file = (File) payload.getObject();
                if(!resources.containsKey(file)){
                    resources.put(file, Media.create(file));
                    addActor(new ResPanelItem(file.getName()));
                }
            }
        });

        resources.keySet().forEach(file -> addActor(new ResPanelItem(file.getName())));

    }
    public class ResPanelItem extends VisTable {
        public ResPanelItem(String name){
            super();
            add(new VisImage( test)).size(100).row();
            add(new VisLabel(name));
        }
    }
    @Override
    public float getMinWidth() {
        return 0;
    }
    @Override
    public float getMinHeight() {
        return 0;
    }

}

