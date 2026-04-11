package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.ui.Root;

public abstract class SegActor extends Actor {
    private static final Color blue = new Color(0x1ba1e2ff);
    private static final Color lightBlue = new Color(0x5ebdecff);
    private final Segment<?> segment;
    private DragSide dragSide=DragSide.NONE;
    public SegActor(Segment<?> segment) {
        this.segment = segment;
        addListener(new ClickListener(){
            @Override
            public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
                final float edgeWidth = 30;
                if(!(getParent() instanceof TlGroup)){
                    return false;
                }
                if (x < edgeWidth){
                    dragSide = DragSide.FRONT;
                }else if (x > getWidth() - edgeWidth) {
                    dragSide = DragSide.BEHIND;
                }else {
                    dragSide = DragSide.MIDDLE;
                }
                event.stop();
                return true;
            }
            @Override
            public void touchDragged (InputEvent event, float x, float y, int pointer) {
                ((TlGroup) getParent()).segDrag(SegActor.this,x,y);
            }
            @Override
            public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
                ((TlGroup) getParent()).segDragEnd(SegActor.this);
                dragSide=DragSide.NONE;
            }
        });
    }
    @Override
    public void draw(Batch batch, float parentAlpha) {
        Root.getInstance().getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), lightBlue);
        Root.getInstance().getShapeDrawer().rectangle(getX(), getY(), getWidth(), getHeight(), blue, 4);
    }

    public Segment<?> getSegmentData() {
        return segment;
    }
    public DragSide getDragSide(){
        return dragSide;
    }
}
