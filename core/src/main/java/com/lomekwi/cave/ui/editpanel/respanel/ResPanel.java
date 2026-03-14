package com.lomekwi.cave.ui.editpanel.respanel;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.layout.GridGroup;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisImageTextButton;
import com.lomekwi.cave.ui.Root;



public class ResPanel extends Container<ResPanel.ResPanelCon> {
    private final TextureRegionDrawable test;
    public ResPanel(){
        super();
        test = new TextureRegionDrawable(new Texture("libgdx.png"));
        setActor(new ResPanelCon());
        fill();
        getActor().addActor(new ResPanelItem("test"));
    }
    public class ResPanelCon extends GridGroup{
        public ResPanelCon(){
            super(64, 4);

            Root.getInstance().getDragAndDrop().addTarget(new DragAndDrop.Target(this) {
                @Override
                public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                    System.out.println("drag");
                    return true;
                }

                @Override
                public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                    addActor(new ResPanelItem(((FileHandle) payload.getObject()).name()));
                    System.out.println("drop");
                }
            });
        }
    }
    public class ResPanelItem extends VisImage {
        public ResPanelItem(String name){
            super(test);
        }
    }
}
