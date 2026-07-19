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
import com.lomekwi.cave.ui.Colors;

public abstract class SegActor extends Actor {
    private final Segment segment;
    private DragSide dragSide=DragSide.NONE;
    private final Rectangle scissors = new Rectangle();
    private final Rectangle bounds = new Rectangle();
    private boolean hovered;
    private boolean menuInitialized;
    private static final Color hoverColor = new Color(1, 1, 1, 0.25f);
    public SegActor(Segment segment) {
        this.segment = segment;
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
                if (getParent()==null || !(getParent() instanceof TlGroup tlGroup)) {
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
                    boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
                    tlGroup.selectSegment(segment, ctrl);
                    return true;
                } else {
                    getMenu().setContext(SegActor.this,((TlGroup)getParent()).xToAbsoluteTime(getX()+x));
                    return false;
                }
            }
            @Override
            public void touchDragged (InputEvent event, float x, float y, int pointer) {
                ((TlGroup) getParent()).segDrag(SegActor.this,x,y);
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
        App.root.getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), Colors.ACCENT_LIGHT);
    }
    protected void drawBorder(){
        var s=getSegment().isSelected();
        App.root.getShapeDrawer().rectangle(getX(), getY(), getWidth(), getHeight(), s ? Color.WHITE : Colors.ACCENT, s ? 6 : 2);
    }
    private void drawSelectionOverlay(){
        if (hovered) {
            App.root.getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), hoverColor);
        }
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
        if (getParent() instanceof TlGroup g) return g.segMenu;
        return null;
    }

    void initMenu() {
        if (!menuInitialized) {
            SegMenu menu = getMenu();
            if (menu != null) {
                addListener(menu.getDefaultInputListener());
                menuInitialized = true;
            }
        }
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
