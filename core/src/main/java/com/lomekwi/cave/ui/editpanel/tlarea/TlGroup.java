package com.lomekwi.cave.ui.editpanel.tlarea;

import static com.lomekwi.cave.util.Units.SECOND;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.google.common.collect.Range;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.resource.media.Media;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.project.ProjectEvents;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.timeline.playback.PlaybackState;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.util.MimeType;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import space.earlygrey.shapedrawer.ShapeDrawer;

import static com.badlogic.gdx.Input.Keys.*;

public class TlGroup extends Group {

    private final ShapeDrawer shapeDrawer = Root.getInstance().getShapeDrawer();

    private final Timeline timeline;
    private final Playhead playhead;
    private final Project project;

    private long viewStartTime;
    private long viewDurationTime;

    private float trackHeight;
    /**
     * 轨道的y偏移，一个非负数。当值大于0，则将所有轨道向下移动y偏移量
     */
    private float trackYShift;

    private boolean dirty = true;

    private final Vector2 pointer = new Vector2();

    private final Color black = new Color(Color.BLACK).add(0, 0, 0, -0.5f);

    public TlGroup(Project project) {


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
                    viewStartTime = Math.max(viewStartTime + (xToAbsoluteTime(amountY * 30) - xToAbsoluteTime(0)), 0);

                } else {
                    trackYShift = Math.max(0, trackYShift + amountY * 10);
                }

                dirty = true;
                return true;
            }

            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == SPACE) {
                    if (playhead.getStates().contains(PlaybackState.PLAYING)) {
                        playhead.clearState(PlaybackState.PLAYING);
                    } else {
                        playhead.setState(PlaybackState.PLAYING);
                    }
                }
                return true;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                Root.getInstance().getStage().setScrollFocus(TlGroup.this);
                Root.getInstance().getStage().setKeyboardFocus(TlGroup.this);
            }
        });

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                final long t = xToAbsoluteTime(x);
                playhead.seek(Math.max(t, 0));
            }
        });


        Root.getInstance().getDragAndDrop().addTarget(new DragAndDrop.Target(this) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                if (!(payload.getObject() instanceof File)) {
                    return false;
                }
                File file = (File) payload.getObject();
                String mimeType = MimeType.detectMimeType(file);
                return mimeType != null && Media.isSupported(mimeType);
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                try {
                    File file = (File) payload.getObject();
                    Segment s = project.segFactory.get(file);
                    s.origin = xToAbsoluteTime(x);
                    timeline.add(timeline.getTrack(yToTrackIndex(y)), s, xToAbsoluteTime(x), 10*SECOND);
                    dirty = true;
                } catch (IOException e) {
                    Gdx.app.error("TlGroup", "拖拽文件失败: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void act(float delta) {
        super.act(delta);

        if (dirty) {
            clearChildren(false);

            final Range<Long> visibleRange = Range.closedOpen(viewStartTime, viewStartTime + viewDurationTime);
            for (int i = 0; i < timeline.getTracks().size(); i++) {
                final Track track = timeline.getTracks().get(i);

                for (final Map.Entry<Range<Long>, Segment> entry : track.getSources().subRangeMap(visibleRange).asMapOfRanges().entrySet()) {
                    SegActor actor = entry.getValue().getActor();
                    Range<Long> r = actor.getSegment().getRange();
                    switch (actor.getDragSide()) {
                        case FRONT:
                        case BEHIND:
                            actor.setPosition(
                                absoluteTimeToX(r.lowerEndpoint()),
                                getHeight() + trackYShift - (i + 1) * trackHeight
                            );
                            Stage s = getStage();
                            segDrag(actor, stageToLocalCoordinates(s.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY()))).x - actor.getX(), Float.NaN/*此时不可能用到*/);
                            actor.setHeight(trackHeight);
                            break;
                        case MIDDLE:
                            actor.setSize(
                                absoluteTimeToX(r.upperEndpoint()) - absoluteTimeToX(r.lowerEndpoint()),
                                trackHeight
                            );
                            break;
                        case NONE:
                            actor.setPosition(
                                absoluteTimeToX(r.lowerEndpoint()),
                                getHeight() + trackYShift - (i + 1) * trackHeight
                            );
                            actor.setSize(
                                absoluteTimeToX(r.upperEndpoint()) - absoluteTimeToX(r.lowerEndpoint()),
                                trackHeight
                            );
                            break;
                    }
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
        return viewStartTime + (long) ((x / getWidth()) * viewDurationTime);
    }

    @Subscribe
    public void onProjectFronted(ProjectEvents.ProjectFrontedEvent e) {
        Root.getInstance().getStage().setScrollFocus(this);
        Root.getInstance().getStage().setKeyboardFocus(this);
    }

    float firstX = Float.NaN, firstY = Float.NaN;

    protected void  segDrag(SegActor actor, float diffToActorX, float diffToActorY) {
        Track t = actor.getSegment().getTrack();
        Map.Entry<Range<Long>, Segment> r = actor.getSegment().getEntry();

        switch (actor.getDragSide()) {
            case FRONT: {
                float upper = actor.getX() + actor.getWidth();
                float target = actor.getX() + diffToActorX;
                if (target >= upper) return;
                target = Math.max(target, absoluteTimeToX(0));

                Range<Long> nr = Range.closedOpen(xToAbsoluteTime(target), r.getKey().upperEndpoint());

                if (!t.isFree(r, nr)) {
                    long minStart = 0;
                    Range<Long> hullRange = nr.span(r.getKey());

                    Map<Range<Long>, Segment> occupiedRanges = t.getSources().subRangeMap(hullRange).asMapOfRanges();
                    for (Map.Entry<Range<Long>, Segment> entry : occupiedRanges.entrySet()) {
                        if (!entry.getValue().equals(r.getValue())) {
                            minStart = entry.getKey().upperEndpoint();
                            break;
                        }
                    }

                    target = Math.max(absoluteTimeToX(minStart), absoluteTimeToX(0));
                    nr = Range.closedOpen(xToAbsoluteTime(target), r.getKey().upperEndpoint());
                }

                actor.setX(target);
                actor.setWidth(upper - target);
                timeline.resize(t, r, nr.lowerEndpoint(), nr.upperEndpoint() - nr.lowerEndpoint());
                break;
            }
            case BEHIND: {
                if (diffToActorX < 1f) return;
                float newWidth = diffToActorX;
                float upper = actor.getX() + newWidth;
                Range<Long> nr = Range.closedOpen(r.getKey().lowerEndpoint(), xToAbsoluteTime(upper));

                if (!t.isFree(r, nr)) {
                    long maxEnd = timeline.getLength();
                    Range<Long> hullRange = nr.span(r.getKey());

                    Map<Range<Long>, Segment> occupiedRanges = t.getSources().subRangeMap(hullRange).asDescendingMapOfRanges();
                    for (Map.Entry<Range<Long>, Segment> entry : occupiedRanges.entrySet()) {
                        if (!entry.getValue().equals(r.getValue())) {
                            maxEnd = entry.getKey().lowerEndpoint();
                            break;
                        }
                    }

                    upper = absoluteTimeToX(maxEnd);
                    newWidth = upper - actor.getX();
                    nr = Range.closedOpen(r.getKey().lowerEndpoint(), xToAbsoluteTime(upper));
                }

                actor.setWidth(newWidth);
                timeline.resize(t, r, r.getKey().lowerEndpoint(), nr.upperEndpoint() - nr.lowerEndpoint());
                break;
            }
            case MIDDLE: {
                playhead.setState(PlaybackState.SEEKING);

                if (Float.isNaN(firstX)) {
                    firstX = diffToActorX;
                    firstY = diffToActorY;
                    return;
                }

                float oldx = actor.getX();

                float deltaX = diffToActorX - firstX,
                    deltaY = diffToActorY - firstY,
                    targetX = Math.max(oldx + deltaX, 0f),
                    targetY = Math.min(actor.getY() + deltaY, getHeight() - trackYShift - trackHeight);

                long target = xToAbsoluteTime(targetX);
                long duration = r.getKey().upperEndpoint() - r.getKey().lowerEndpoint();
                Range<Long> nr = Range.closedOpen(target, target + duration);

                Track newTrack = timeline.getTrack(Math.max(0, yToTrackIndex(targetY + trackHeight / 2)));
                actor.setPosition(targetX, targetY);
                if(newTrack.isFree(r, nr)) {
                    timeline.move(t, newTrack, r, target, duration);
                    long deltaTime = xToAbsoluteTime(targetX) - xToAbsoluteTime(oldx);
                    r.getValue().origin += deltaTime;
                }
            }
        }
    }
    public void removeSeg(SegActor segActor){
        removeActor(segActor);
        Segment s = segActor.getSegment();
        timeline.remove(s.getTrack(),s.getRange().lowerEndpoint(),s.getRange().upperEndpoint()-s.getRange().lowerEndpoint());
    }

    private int yToTrackIndex(float y) {
        final float top = getHeight() - trackYShift;
        final float distance = top - y;
        return (int) Math.floor(distance / trackHeight);
    }

    private float trackIndexToTopY(int index) {
        return getHeight() + trackYShift - index * trackHeight;
    }

    private float trackIndexToBottomY(int index) {
        return trackIndexToTopY(index) - trackHeight;
    }

    protected void segDragEnd(SegActor actor) {
        dirty = true;
        firstX = Float.NaN;
    }

    @Override
    public void sizeChanged() {
        dirty = true;
    }
}
