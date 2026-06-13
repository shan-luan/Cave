package com.lomekwi.cave.ui.editpanel.mediapool;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.layout.FlowGroup;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.google.common.collect.Multimap;
import com.lomekwi.cave.resource.Resource;

import com.lomekwi.cave.app.App;

import java.io.File;


public class MediaPool extends FlowGroup {
    private final TextureRegionDrawable test;

    public MediaPool(Multimap<File, Resource> resources){
        super(false);

        test = new TextureRegionDrawable(new Texture("libgdx.png"));
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);

        DragAndDrop dnd = App.root.getDragAndDrop();

        dnd.addTarget(new DragAndDrop.Target(this) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                return payload.getObject() instanceof File;
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                if (source.getActor().isDescendantOf(MediaPool.this)) return;

                File file = (File) payload.getObject();
                if(!resources.containsKey(file)){
                    MediaPoolItem item = new MediaPoolItem(file);
                    addActor(item);
                    registerDragSource(dnd, item);
                }
            }
        });

        resources.keySet().forEach(file -> {
            MediaPoolItem item = new MediaPoolItem(file);
            addActor(item);
            registerDragSource(dnd, item);
        });
    }

    private void registerDragSource(DragAndDrop dnd, MediaPoolItem item) {
        dnd.addSource(new DragAndDrop.Source(item) {
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                DragAndDrop.Payload payload = new DragAndDrop.Payload();
                payload.setObject(item.getFile());
                payload.setDragActor(new MediaPoolItem(item.getFile()));
                return payload;
            }
        });
    }

    public class MediaPoolItem extends VisTable {
        private final File file;

        public MediaPoolItem(File file){
            super();
            this.file = file;
            add(new VisImage(test)).size(100).row();
            add(new VisLabel(file.getName()));
        }

        public File getFile() {
            return file;
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
