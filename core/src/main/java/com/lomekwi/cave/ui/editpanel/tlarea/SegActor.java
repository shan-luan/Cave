package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.Null;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.SegmentGroup;

import com.lomekwi.cave.app.App;

public abstract class SegActor extends Actor {
    protected static final Color blue = new Color(0x1ba1e2ff);
    protected static final Color lightBlue = new Color(0x5ebdecff);
    protected static final Color darkBlue = new Color(0x1ba1e2ff);
    private final Segment segment;
    private DragSide dragSide=DragSide.NONE;
    private boolean dragInvalid;
    private final Rectangle scissors = new Rectangle();
    private final Rectangle bounds = new Rectangle();
    private boolean hovered;
    private static final Color hoverColor = new Color(1, 1, 1, 0.25f);
    private static final Color selectedColor = new Color(0, 1, 0.3f, 0.4f);
    private static final Color dragInvalidColor = new Color(1, 0, 0, 0.4f);
    public SegActor(Segment segment) {
        this.segment = segment;
        addListener(getMenu().getDefaultInputListener());
        addListener(new InputListener(){
            final float edgeWidth = 30;
            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                hovered = true;
                if (x < edgeWidth){
                    setCursor(Cursor.SystemCursor.HorizontalResize);
                }else if (x > getWidth() - edgeWidth) {
                    setCursor(Cursor.SystemCursor.HorizontalResize);
                }else {
                    setCursor(Cursor.SystemCursor.AllResize);
                }
                SegmentGroup group = segment.getGroup();
                if (group != null) {
                    for (Segment s : group.getSegments()) {
                        if (s != segment) {
                            s.getActor().setHovered(true);
                        }
                    }
                }
                return false;
            }
            @Override
            public void exit(InputEvent event, float x, float y, int pointer, @Null Actor toActor) {
                hovered = false;
                if(dragSide==DragSide.NONE) {
                    setCursor(Cursor.SystemCursor.Arrow);
                }
                SegmentGroup group = segment.getGroup();
                if (group != null) {
                    for (Segment s : group.getSegments()) {
                        if (s != segment) {
                            s.getActor().setHovered(false);
                        }
                    }
                }
            }

            @Override
            public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
                if (getParent()==null || !(getParent() instanceof TlGroup)) {
                    return false;
                }
                if (button == Input.Buttons.LEFT) {
                    if (x < edgeWidth) {
                        dragSide = DragSide.FRONT;
                    } else if (x > getWidth() - edgeWidth) {
                        dragSide = DragSide.BEHIND;
                    } else {
                        dragSide = DragSide.MIDDLE;
                    }
                    event.stop();
                    return true;
                } else {
                    getMenu().setContext((TlGroup) getParent(), SegActor.this,((TlGroup)getParent()).xToAbsoluteTime(getX()+x));
                    return false;
                }
            }
            @Override
            public void touchDragged (InputEvent event, float x, float y, int pointer) {
                ((TlGroup) getParent()).segDrag(SegActor.this,x,y);
                ((TlGroup) getParent()).seekPlayheadAtX(getX() + x);
            }
            @Override
            public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
                ((TlGroup) getParent()).segDragEnd(SegActor.this);
                dragSide=DragSide.NONE;
                setCursor(Cursor.SystemCursor.Arrow);
            }
        });
    }
    @Override
    public void draw(Batch batch, float parentAlpha) {
            ScissorStack.calculateScissors(App.root.getStage().getCamera(), batch.getTransformMatrix(),bounds , scissors);
        if (ScissorStack.pushScissors(scissors)) {
            drawContent(batch,parentAlpha);
            drawBorder();
            drawSelectionOverlay();
            batch.flush();
            ScissorStack.popScissors();
        }
    }
    protected void drawContent(Batch batch, float parentAlpha){
        App.root.getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), lightBlue);
    }
    protected void drawBorder(){
        App.root.getShapeDrawer().rectangle(getX(), getY(), getWidth(), getHeight(), blue, 2);
    }
    private void drawSelectionOverlay(){
        if (getSegment().isSelected()) {
            App.root.getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), selectedColor);
        }
        if (hovered) {
            App.root.getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), hoverColor);
        }
        if (dragInvalid) {
            App.root.getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), dragInvalidColor);
        }
    }

    public void setDragInvalid(boolean invalid) {
        this.dragInvalid = invalid;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public Segment getSegment() {
        return segment;
    }
    public DragSide getDragSide(){
        return dragSide;
    }
    private void setCursor(Cursor.SystemCursor cursor){
        if (Gdx.app.getType() != Application.ApplicationType.Desktop) return;
        Gdx.graphics.setSystemCursor(cursor);
    }
    public SegMenu getMenu() {
        return SegMenu.getInstance();
    }
    @Override
    protected void positionChanged(){
        bounds.set(getX(),getY(),getWidth(),getHeight());
    }
    @Override
    protected void sizeChanged(){
        bounds.set(getX(),getY(),getWidth(),getHeight());
    }
}
