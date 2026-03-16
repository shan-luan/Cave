package com.lomekwi.cave.ui.editpanel.filetree;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Tree;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.lomekwi.cave.ui.Root;

import java.io.File;

public class FileTreeNode extends Tree.Node<FileTreeNode, File, VisLabel> {
    public FileTreeNode(File file){
        super();
        setActor(new DraggableLabel(file.getName()));
        setValue(file);
    }
    public class DraggableLabel extends VisLabel {
        private VisLabel dragActor;
        public DraggableLabel(String text) {
            super(text);
            Root.getInstance().getDragAndDrop().addSource(new DragAndDrop.Source(this) {
                @Override
                public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                    DragAndDrop.Payload payload = new DragAndDrop.Payload();
                    payload.setObject(getValue());

                    dragActor = new VisLabel(getText().toString());
                    payload.setDragActor(dragActor);
                    return payload;
                }
                @Override
                public void drag(InputEvent event, float x, float y, int pointer) {
                    super.drag(event, x, y, pointer);
                }

                @Override
                public void dragStop(InputEvent event, float x, float y, int pointer, DragAndDrop.Payload payload, DragAndDrop.Target target) {
                    dragActor=null;
                }
            });
        }
    }
}
