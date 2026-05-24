package com.lomekwi.cave.ui.editpanel.filetree;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Tree;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.lomekwi.cave.ui.Root;

import java.io.File;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

public class FileTreeNode extends Tree.Node<FileTreeNode, File, VisLabel> {
    private boolean childrenLoaded = false;
    private static final FileTreeNode PLACEHOLDER_NODE = new FileTreeNode(null);

    public FileTreeNode(File file){
        super();
        setActor(new DraggableLabel(file != null ? file.getName() : ""));
        setValue(file);

        if (file != null && file.isDirectory()) {
            Gdx.app.debug("FileTreeNode", i18n("创建目录节点: ") + file.getName());
            add(PLACEHOLDER_NODE);
        }
    }

    @Override
    public void setExpanded(boolean expanded) {
        super.setExpanded(expanded);

        if (expanded && !childrenLoaded) {
            Gdx.app.debug("FileTreeNode", i18n("展开目录，开始加载子节点: ") + getValue().getName());
            loadChildren();
            childrenLoaded = true;
            Gdx.app.debug("FileTreeNode", i18n("子节点加载完成: ") + getValue().getName());
        }
    }

    private void loadChildren() {
        File file = getValue();
        if (file != null && file.isDirectory()) {
            getChildren().removeValue(PLACEHOLDER_NODE, true);

            File[] children = file.listFiles();
            if (children != null) {
                Gdx.app.debug("FileTreeNode", i18n("找到 ") + children.length + i18n(" 个子项"));
                for (File child : children) {
                    FileTreeNode childNode = new FileTreeNode(child);
                    add(childNode);
                }
            }
        }
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
