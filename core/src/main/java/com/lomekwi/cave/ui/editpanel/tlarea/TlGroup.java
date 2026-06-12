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
import com.lomekwi.cave.resource.media.MediaFactory;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.project.ProjectFrontedEvent;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.util.MimeType;

import com.lomekwi.cave.app.App;

import java.io.File;
import java.io.IOException;

import space.earlygrey.shapedrawer.ShapeDrawer;

import static com.badlogic.gdx.Input.Keys.*;

public class TlGroup extends Group {

    private final ShapeDrawer shapeDrawer = App.root.getShapeDrawer();

    private final Timeline timeline;
    private final Playhead playhead;
    private final Project project;

    final ViewState view = new ViewState();

    private boolean dirty = true;

    private static final float KEY_HORIZONTAL_SPEED = 1200f;
    private static final float KEY_VERTICAL_SPEED = 600f;

    private final Vector2 pointer = new Vector2();

    private final Color black = new Color(Color.BLACK).add(0, 0, 0, -0.5f);

    public TlGroup(Project project) {


        this.project = project;
        this.timeline = project.timeline;
        this.playhead = project.playhead;

        project.projEventBus.register(this);


        this.view.startTime = 0;
        this.view.durationTime = Math.max(project.timeline.getLength(), 30 * SECOND);
        this.view.trackHeight = 80;

        addListener(new InputListener() {
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                final Input ip = Gdx.input;

                if (ip.isKeyPressed(CONTROL_LEFT) && ip.isKeyPressed(SHIFT_LEFT)) {
                    view.adjustTrackHeight(amountY * 10);

                } else if (ip.isKeyPressed(CONTROL_LEFT)) {
                    view.scrollVertical(amountY * 10);

                } else if (ip.isKeyPressed(SHIFT_LEFT)) {
                    view.scrollHorizontal(amountY * 30, getWidth());

                } else {
                    if (!view.zoom(amountY, x / getWidth())) return true;
                }

                dirty = true;
                return true;
            }

            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == SPACE) {
                    playhead.setPlaying(!playhead.isPlaying());
                }
                return true;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                App.root.getStage().setScrollFocus(TlGroup.this);
                App.root.getStage().setKeyboardFocus(TlGroup.this);
            }
        });

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                final long t = xToAbsoluteTime(x);
                playhead.seek(Math.max(t, 0));
            }
        });


        App.root.getDragAndDrop().addTarget(new DragAndDrop.Target(this) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                if (!(payload.getObject() instanceof File)) {
                    return false;
                }
                File file = (File) payload.getObject();
                String mimeType = MimeType.detectMimeType(file);
                return mimeType != null && MediaFactory.isSupported(mimeType);
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                try {
                    File file = (File) payload.getObject();
                    Segment s = project.segFactory.get(file);
                    s.setOrigin(xToAbsoluteTime(x));
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

        final Input ip = Gdx.input;

        if (getStage() != null && getStage().getKeyboardFocus() == this
            && (ip.isKeyPressed(D) || ip.isKeyPressed(A) || ip.isKeyPressed(S) || ip.isKeyPressed(W))) {
            final float timePerPixel = (float) view.durationTime / getWidth();

            if (ip.isKeyPressed(D)) {
                view.startTime += (long) (KEY_HORIZONTAL_SPEED * delta * timePerPixel);
            }
            if (ip.isKeyPressed(A)) {
                view.startTime = Math.max(0, view.startTime - (long) (KEY_HORIZONTAL_SPEED * delta * timePerPixel));
            }
            if (ip.isKeyPressed(S)) {
                view.trackYShift = Math.max(0, view.trackYShift + KEY_VERTICAL_SPEED * delta);
            }
            if (ip.isKeyPressed(W)) {
                view.trackYShift = Math.max(0, view.trackYShift - KEY_VERTICAL_SPEED * delta);
            }

            dirty = true;
        }

        if (dirty) {
            clearChildren(false);

            var visibleRange = view.visibleRange();
            for (int i = 0; i < timeline.getTracks().size(); i++) {
                final Track track = timeline.getTracks().get(i);

                for (var entry : track.getSubRangeMapAsEntrySet(visibleRange)) {
                    SegActor actor = entry.getValue().getActor();
                    var r = actor.getSegment().getRange();
                    switch (actor.getDragSide()) {
                        case FRONT:
                        case BEHIND:
                            actor.setPosition(
                                absoluteTimeToX(r.lowerEndpoint()),
                                getHeight() + view.trackYShift - (i + 1) * view.trackHeight
                            );
                            Stage s = getStage();
                            segDrag(actor, stageToLocalCoordinates(s.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY()))).x - actor.getX(), Float.NaN/*此时不可能用到*/);
                            actor.setHeight(view.trackHeight);
                            break;
                        case MIDDLE:
                            actor.setSize(
                                absoluteTimeToX(r.upperEndpoint()) - absoluteTimeToX(r.lowerEndpoint()),
                                view.trackHeight
                            );
                            break;
                        case NONE:
                            actor.setPosition(
                                absoluteTimeToX(r.lowerEndpoint()),
                                getHeight() + view.trackYShift - (i + 1) * view.trackHeight
                            );
                            actor.setSize(
                                absoluteTimeToX(r.upperEndpoint()) - absoluteTimeToX(r.lowerEndpoint()),
                                view.trackHeight
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
        float offset = ((view.trackYShift % view.trackHeight) + view.trackHeight) % view.trackHeight;
        float startY = getHeight() + offset;

        for (float y = startY; y > -view.trackHeight; y -= view.trackHeight) {
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

    float absoluteTimeToX(long time) {
        return view.timeToX(time, getWidth());
    }

    long xToAbsoluteTime(float x) {
        return view.xToTime(x, getWidth());
    }

    @Subscribe
    public void onProjectFronted(ProjectFrontedEvent e) {
        App.root.getStage().setScrollFocus(this);
        App.root.getStage().setKeyboardFocus(this);
    }

    float firstX = Float.NaN, firstY = Float.NaN;

    protected void  segDrag(SegActor actor, float diffToActorX, float diffToActorY) {
        Track t = actor.getSegment().getTrack();
        var e = actor.getSegment().getEntry();
        var r = e.getKey();

        switch (actor.getDragSide()) {
            case FRONT: {
                float upper = actor.getX() + actor.getWidth();
                float target = actor.getX() + diffToActorX;
                if (target >= upper) return;
                target = Math.max(target, absoluteTimeToX(0));

                //nr:new range
                Range<Long> nr = Range.closedOpen(xToAbsoluteTime(target), r.upperEndpoint());

                if (!t.isFree(e, nr)) {
                    long minStart = t.getEntry(r.lowerEndpoint()+1,-1,true).getKey().upperEndpoint();
                    target = Math.max(absoluteTimeToX(minStart), absoluteTimeToX(0));
                    nr = Range.closedOpen(xToAbsoluteTime(target), r.upperEndpoint());
                }

                actor.setX(target);
                actor.setWidth(upper - target);
                timeline.resize(t, e, nr.lowerEndpoint(), nr.upperEndpoint() - nr.lowerEndpoint());
                break;
            }
            case BEHIND: {
                if (diffToActorX < 1f) return;
                float newWidth = diffToActorX;
                float upper = actor.getX() + newWidth;
                Range<Long> nr = Range.closedOpen(r.lowerEndpoint(), xToAbsoluteTime(upper));

                if (!t.isFree(e, nr)) {
                    long maxEnd = t.getEntry(r.upperEndpoint()-1,1,true).getKey().lowerEndpoint();
                    upper = absoluteTimeToX(maxEnd);
                    newWidth = upper - actor.getX();
                    nr = Range.closedOpen(r.lowerEndpoint(), xToAbsoluteTime(upper));
                }

                actor.setWidth(newWidth);
                timeline.resize(t, e, r.lowerEndpoint(), nr.upperEndpoint() - nr.lowerEndpoint());
                break;
            }
            case MIDDLE: {

                if (Float.isNaN(firstX)) {
                    firstX = diffToActorX;
                    firstY = diffToActorY;
                    return;
                }

                float oldx = actor.getX();

                float deltaX = diffToActorX - firstX,
                    deltaY = diffToActorY - firstY,
                    targetX = Math.max(oldx + deltaX, 0f),
                    targetY = Math.min(actor.getY() + deltaY, getHeight() - view.trackYShift - view.trackHeight);

                long target = xToAbsoluteTime(targetX);
                long duration = r.upperEndpoint() - r.lowerEndpoint();
                var nr = Range.closedOpen(target, target + duration);

                var newTrack = timeline.getTrack(Math.max(0, yToTrackIndex(targetY + view.trackHeight / 2)));
                actor.setPosition(targetX, targetY);
                if(newTrack.isFree(e, nr)) {
                    timeline.move(t, newTrack, e, target, duration);
                    long deltaTime = xToAbsoluteTime(targetX) - xToAbsoluteTime(oldx);
                    e.getValue().offsetOrigin(deltaTime);
                }
            }
        }
    }
    public void removeSeg(SegActor segActor){
        removeActor(segActor);
        Segment s = segActor.getSegment();
        timeline.remove(s.getTrack(),s.getRange());
    }
    public void split(SegActor segActor,long time){
        dirty=true;
        timeline.split(segActor.getSegment().getTrack(),time);
    }

    private int yToTrackIndex(float y) {
        final float top = getHeight() - view.trackYShift;
        final float distance = top - y;
        return (int) Math.floor(distance / view.trackHeight);
    }

    private float trackIndexToTopY(int index) {
        return getHeight() + view.trackYShift - index * view.trackHeight;
    }

    private float trackIndexToBottomY(int index) {
        return trackIndexToTopY(index) - view.trackHeight;
    }

    protected void segDragEnd(SegActor actor) {
        dirty = true;
        firstX = Float.NaN;
    }

    @Override
    public void sizeChanged() {
        dirty = true;
    }

    // -------------------------------------------------------------------------
    // 内部类：视图状态
    // -------------------------------------------------------------------------

    static class ViewState {
        long startTime;
        long durationTime;
        float trackHeight;
        float trackYShift;

        /** 时间 -> 像素 x 坐标 */
        float timeToX(long time, float width) {
            return (float) (time - startTime) / durationTime * width;
        }

        /** 像素 x 坐标 -> 时间 */
        long xToTime(float x, float width) {
            return startTime + (long) ((x / width) * durationTime);
        }

        /** 可见时间范围 */
        Range<Long> visibleRange() {
            return Range.closedOpen(startTime, startTime + durationTime);
        }

        /**
         * 以锚点为中心缩放。返回 false 表示缩放无效（缩放因子 <= 0）
         */
        boolean zoom(float amountY, float anchorXRatio) {
            final long oldDuration = durationTime;
            final float scaleFactor = 1f + amountY * 0.1f;
            if (scaleFactor <= 0f) return false;

            long newDuration = (long) (oldDuration * scaleFactor);
            if (newDuration <= SECOND) newDuration = SECOND;

            final long anchorTime = startTime + (long) (anchorXRatio * oldDuration);
            durationTime = newDuration;
            startTime = Math.max(anchorTime - (long) (anchorXRatio * newDuration), 0);
            return true;
        }

        /** 水平滚动（左右移动视图），deltaPixels > 0 向右 */
        void scrollHorizontal(float deltaPixels, float width) {
            startTime = Math.max(startTime + (xToTime(deltaPixels, width) - xToTime(0, width)), 0);
        }

        /** 垂直滚动（上下移动轨道偏移），delta > 0 向下 */
        void scrollVertical(float delta) {
            trackYShift = Math.max(0, trackYShift + delta);
        }

        /** 调整轨道高度，不小于 10 */
        void adjustTrackHeight(float delta) {
            trackHeight = Math.max(trackHeight + delta, 10);
        }
    }
}
