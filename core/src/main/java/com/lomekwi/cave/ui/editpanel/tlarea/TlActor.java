package com.lomekwi.cave.ui.editpanel.tlarea;

import static com.lomekwi.cave.util.Units.SECOND;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.google.common.collect.Range;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.timeline.segments.Segment;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.project.ProjectEvents;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.ui.Root;

import java.util.Map;

import space.earlygrey.shapedrawer.ShapeDrawer;

import static com.badlogic.gdx.Input.Keys.*;

public class TlActor extends Group {

    private final ShapeDrawer shapeDrawer=Root.getInstance().getShapeDrawer();

    private final Timeline timeline;
    private final Playhead playhead;
    private final Project project;

    private long viewStartTime;
    private long viewDurationTime;

    private float trackHeight;
    private float trackYShift;

    private boolean dirty = true;

    private final Color black = new Color(Color.BLACK).add(0,0,0,-0.5f);

    public TlActor(Project project) {


        this.project = project;
        this.timeline = project.timeline;
        this.playhead = project.playhead;

        project.projEventBus.register(this);


        this.viewStartTime = 0;
        this.viewDurationTime = Math.max(project.timeline.getLength(), 30 * SECOND);
        this.trackHeight = 80;

        addListener(new InputListener() {
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                final Input ip = Gdx.input;

                if (ip.isKeyPressed(CONTROL_LEFT) && ip.isKeyPressed(SHIFT_LEFT)) {
                    trackHeight = Math.max(trackHeight + amountY * 10, 10);

                } else if (ip.isKeyPressed(CONTROL_LEFT)) {
                    final float ratio = x / getWidth();
                    final long oldDuration = viewDurationTime;

                    final float scaleFactor = 1f + amountY * 0.1f;
                    if (scaleFactor <= 0f) return true;

                    long newDuration = (long) (oldDuration * scaleFactor);
                    if (newDuration <= SECOND) newDuration = SECOND;

                    final long anchorTime = viewStartTime + (long) (ratio * oldDuration);

                    viewDurationTime = newDuration;
                    viewStartTime = Math.max(anchorTime - (long) (ratio * newDuration), 0);

                } else if (ip.isKeyPressed(SHIFT_LEFT)) {
                    viewStartTime = Math.max(viewStartTime + (long) (amountY * SECOND), 0);

                } else {
                    trackYShift += amountY * 10;
                }

                dirty = true;
                return true;
            }

            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == SPACE) {
                    project.playhead.setPlaying(!project.playhead.isPlaying());
                }
                return true;
            }
        });

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                final long t = xToAbsoluteTime(x);
                project.playhead.setTime(Math.max(t, 0));
            }
        });

        addListener(event -> {
            if (event instanceof FocusListener.FocusEvent) {
                final FocusListener.FocusEvent e = (FocusListener.FocusEvent) event;
                if (Root.getInstance().getFrontendProject() == project && !e.isFocused()) {
                    e.cancel();
                    return true;
                }
            }
            return false;
        });
    }
    @Override
    public void act(float delta) {
        super.act(delta);

        if (dirty) {
            clearChildren();

            final Range<Long> visibleRange = Range.closedOpen(viewStartTime, viewStartTime + viewDurationTime);
            for (int i = 0; i < timeline.getTracks().size(); i++) {
                final Track track = timeline.getTracks().get(i);

                //FIXME:截断区间边界导致渲染可能会出现问题
                for (final Map.Entry<Range<Long>, Segment<?>> entry : track.getSources().subRangeMap(visibleRange).asMapOfRanges().entrySet()) {

                    entry.getValue().getActor().setBounds(
                        absoluteTimeToX(entry.getKey().lowerEndpoint()),
                        getHeight() + trackYShift - (i + 1) * trackHeight,
                        absoluteTimeToX(entry.getKey().upperEndpoint()) - absoluteTimeToX(entry.getKey().lowerEndpoint()),
                        trackHeight
                    );
                    addActor(entry.getValue().getActor());
                }
            }

            dirty = false;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        drawBackground();
        drawSplitters();
        super.draw(batch, parentAlpha);
        drawPlayhead();
    }

    private void drawBackground() {
        shapeDrawer.filledRectangle(0, 0, getWidth(), getHeight(), Color.DARK_GRAY);

        final float startX = absoluteTimeToX(0);
        final float endX = absoluteTimeToX(timeline.getLength());

        shapeDrawer.filledRectangle(startX, 0, endX - startX, getHeight(), Color.GRAY);
    }
    private void drawSplitters() {
        float offset = ((trackYShift % trackHeight) + trackHeight) % trackHeight;
        float startY = getHeight() + offset;

        for (float y = startY; y > -trackHeight; y -= trackHeight) {
            shapeDrawer.line(0, y, getWidth(), y, black);
        }
    }


    private void drawPlayhead() {
        final float x = absoluteTimeToX(playhead.getTime());

        shapeDrawer.filledTriangle(
            x - 10, getHeight(),
            x + 10, getHeight(),
            x, getHeight() - 20,
            Color.RED
        );

        shapeDrawer.line(x, 0, x, getHeight(), Color.RED, 3);
    }

    public void dispose() {
        project.projEventBus.unregister(this);
    }

    private float absoluteTimeToX(long time) {
        return (float) (time - viewStartTime) / viewDurationTime * getWidth();
    }

    private long xToAbsoluteTime(float x) {
        final float ratio = x / getWidth();
        return viewStartTime + (long) (ratio * viewDurationTime);
    }

    @Subscribe
    public void onProjectFronted(ProjectEvents.ProjectFrontedEvent e) {
        Root.getInstance().getStage().setScrollFocus(this);
        Root.getInstance().getStage().setKeyboardFocus(this);
    }
    protected void segLengthDrag(SegActor actor,float x,DragSide side){
        switch (side) {
            case FRONT:
                float upper = actor.getX() + actor.getWidth();
                actor.setX(getX()+x);
                actor.setWidth(upper - x);
                break;
            case BEHIND:
                actor.setWidth(x);
                break;
        }
    }
}
