package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.lomekwi.cave.ui.Root;

public abstract class SegActor extends Actor {
    private DragSide dragSide;
    public SegActor() {
        addListener(new InputListener(){
            @Override
            public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
                final float edgeWidth = 10;
                if(!(getParent() instanceof TlActor)){
                    return false;
                }
                if (x < edgeWidth){
                    dragSide = DragSide.FRONT;
                    System.out.println("f");
                    return true;
                }else if (x > getWidth() - edgeWidth) {
                    dragSide = DragSide.BEHIND;
                    System.out.println("b");
                    return true;
                }else {
                    return false;
                }
            }
            @Override
            public void touchDragged (InputEvent event, float x, float y, int pointer) {
                ((TlActor) getParent()).segLengthDrag(SegActor.this,x,dragSide);}
        });}
    @Override
    public void draw(Batch batch, float parentAlpha) {
        Root.getInstance().getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), Color.SKY);
        Root.getInstance().getShapeDrawer().rectangle(getX(), getY(), getWidth(), getHeight(), Color.BLUE, 10);
    }
}
