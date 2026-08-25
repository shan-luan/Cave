package com.lomekwi.cave.ui.editpanel.tlarea;

import static com.lomekwi.cave.util.Units.SECOND;
import static com.lomekwi.cave.util.Units.niceScale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.DragListener;
import com.google.common.collect.Range;
import com.lomekwi.cave.app.shortcut.ShortcutAction;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.SegmentGroup;
import com.lomekwi.cave.timeline.SegmentSelectedEvent;
import com.lomekwi.cave.timeline.SegmentSet;
import com.lomekwi.cave.timeline.SegmentSetSelectedEvent;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.Playhead;

import com.lomekwi.cave.app.App;
import com.lomekwi.cave.ui.Colors;
import com.lomekwi.cave.ui.Focusable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import space.earlygrey.shapedrawer.ShapeDrawer;

import static com.badlogic.gdx.Input.Keys.*;

public class TlGroup extends Group implements Focusable {

    private final TimelineRenderer renderer = new TimelineRenderer();
    final SegDragHandler dragHandler = new SegDragHandler();
    public final SegMenu segMenu = new SegMenu(this);
    public final TlGroupMenu tlGroupMenu = new TlGroupMenu(this);

    final Timeline timeline;
    final Playhead playhead;
    final Project project;

    final ViewState view = new ViewState();

    boolean dirty = true;
    final SegmentSet selectedSegments = new SegmentSet();

    private static final float KEY_HORIZONTAL_SPEED = 1200f;
    private static final float KEY_VERTICAL_SPEED = 1200f;

    private final Vector2 pointer = new Vector2();

    boolean marqueeActive;
    float marqueeStartX, marqueeStartY;
    float marqueeEndX, marqueeEndY;

    /** 拖拽吸附时的吸附时间点，-1 表示无吸附 */
    long snapIndicatorTime = -1;

    public TlGroup(Project project) {
        this.project = project;
        this.timeline = project.timeline;
        this.playhead = project.playhead;

        project.projEventBus.register(this);

        this.view.startTime = 0;
        this.view.durationTime = Math.max(project.timeline.getLength(), 30 * SECOND);
        this.view.trackHeight = 80;

        addDefaultListeners();
    }

    private void addDefaultListeners() {
        addListener(new TlGroupInputListener(this));
        addCaptureListener(new TlGroupCaptureListener(this));
        App.root.getDragAndDrop().addTarget(new TlGroupDropTarget(this));
        addListener(new DragListener() {
            {
                setButton(Input.Buttons.MIDDLE);
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (super.touchDown(event, x, y, pointer, button)) {
                    Gdx.graphics.setSystemCursor(Cursor.SystemCursor.AllResize);
                    return true;
                }
                return false;
            }

            @Override
            public void drag(InputEvent event, float x, float y, int pointer) {
                view.scrollHorizontal(-getDeltaX(), getWidth());
                view.trackYShift = Math.max(0, view.trackYShift + getDeltaY());
                dirty = true;
                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.AllResize);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                super.touchUp(event, x, y, pointer, button);
                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
            }
        });
    }

    @Override
    public void act(float delta) {
        super.act(delta);

        if (getStage() != null) {
            pointer.set(Gdx.input.getX(), Gdx.input.getY());
            getStage().screenToStageCoordinates(pointer);
            stageToLocalCoordinates(pointer);

            boolean acted = false;

            if (!App.root.isTextInputFocused() && getStage().getKeyboardFocus() == this) {
                final float timePerPixel = (float) view.durationTime / getWidth();

                if (App.shortcutManager.isActive(Actions.SCROLL_RIGHT)) {
                    view.startTime += (long) (KEY_HORIZONTAL_SPEED * delta * timePerPixel);
                    acted = true;
                }
                if (App.shortcutManager.isActive(Actions.SCROLL_LEFT)) {
                    view.startTime = Math.max(0, view.startTime - (long) (KEY_HORIZONTAL_SPEED * delta * timePerPixel));
                    acted = true;
                }
                if (App.shortcutManager.isActive(Actions.SCROLL_DOWN)) {
                    view.trackYShift = Math.max(0, view.trackYShift + KEY_VERTICAL_SPEED * delta);
                    acted = true;
                }
                if (App.shortcutManager.isActive(Actions.SCROLL_UP)) {
                    view.trackYShift = Math.max(0, view.trackYShift - KEY_VERTICAL_SPEED * delta);
                    acted = true;
                }

                if (App.shortcutManager.isActive(Actions.SEEK)) {
                    seekPlayheadAtX(pointer.x);
                    acted = true;
                }
            }

            if (acted) dirty = true;
        }

        // dirty 时按模型 RangeMap 重建 UI；拖拽中的 Actor 在重建后把鼠标偏移再喂回
        // segDrag，让被拖拽片段即时贴到鼠标位置（这也是 segDrag 必须幂等的原因）。
        if (dirty) {
            clearChildren(false);

            var visibleRange = view.visibleRange();
            for (int i = timeline.getTracks().size() - 1; i >= 0; i--) {
                final Track track = timeline.getTracks().get(i);

                for (var entry : List.copyOf(track.getSubRangeMapAsEntrySet(visibleRange))) {
                    SegActor actor = entry.getValue().getActor();
                    var r = actor.getSegment().getRange();
                    switch (actor.getDragSide()) {
                        case FRONT,BEHIND:
                            actor.setPosition(
                                absoluteTimeToX(r.lowerEndpoint()),
                                getHeight() + view.trackYShift - (i + 1) * view.trackHeight
                            );
                            Stage s = getStage();
                            float feedX = stageToLocalCoordinates(s.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY()))).x - actor.getX();
                            dragHandler.segDrag(actor, feedX, Float.NaN);
                            actor.setHeight(view.trackHeight);
                            break;
                        case MIDDLE:
                            actor.setPosition(
                                absoluteTimeToX(r.lowerEndpoint()),
                                getHeight() + view.trackYShift - (i + 1) * view.trackHeight
                            );
                            actor.setSize(
                                absoluteTimeToX(r.upperEndpoint()) - absoluteTimeToX(r.lowerEndpoint()),
                                view.trackHeight
                            );
                            Stage stage = getStage();
                            Vector2 local = stageToLocalCoordinates(stage.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY())));
                            float fx = local.x - actor.getX();
                            float fy = local.y - actor.getY();
                            dragHandler.segDrag(actor, fx, fy);
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
                    actor.initMenu();
                }
            }

            dirty = false;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        renderer.drawBackground();
        renderer.drawTrackBands();
        renderer.drawTicks();
        batch.setColor(Color.WHITE);
        super.draw(batch, parentAlpha);
        renderer.drawPlayhead();
        if (snapIndicatorTime >= 0) {
            renderer.drawSnapIndicator();
        }
        if (marqueeActive) {
            float x = Math.min(marqueeStartX, marqueeEndX);
            float y = Math.min(marqueeStartY, marqueeEndY);
            float w = Math.abs(marqueeEndX - marqueeStartX);
            float h = Math.abs(marqueeEndY - marqueeStartY);
            renderer.shapeDrawer.filledRectangle(x, y, w, h, new Color(1, 1, 1, 0.3f));
            renderer.shapeDrawer.rectangle(x, y, w, h, Color.WHITE);
        }
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

    void seekPlayheadAtX(float x) {
        playhead.seek(Math.max(xToAbsoluteTime(x), 0));
    }

    public void selectSegment(Segment segment, boolean addToSelection) {
        SegmentGroup group = segment.getGroup();
        if (group != null) {
            if (addToSelection) {
                boolean anySelected = false;
                for (Segment s : group) {
                    if (selectedSegments.contains(s)) { anySelected = true; break; }
                }
                if (anySelected) {
                    for (Segment s : group) {
                        selectedSegments.remove(s);
                        s.setSelected(false);
                    }
                } else {
                    for (Segment s : group) {
                        selectedSegments.add(s);
                        s.setSelected(true);
                    }
                }
            } else {
                clearSelection();
                for (Segment s : group) {
                    selectedSegments.add(s);
                    s.setSelected(true);
                }
            }
            int count = selectedSegments.size();
            if (count == 0) {
                var e = new SegmentSelectedEvent(null, null, 0);
                project.projEventBus.post(e);
                App.appEventBus.post(e);
            } else if (count == 1) {
                Segment remaining = selectedSegments.iterator().next();
                var e = new SegmentSelectedEvent(remaining, remaining.getTrack(), 1);
                project.projEventBus.post(e);
                App.appEventBus.post(e);
            } else {
                var e = new SegmentSelectedEvent(null, null, count);
                project.projEventBus.post(e);
                App.appEventBus.post(e);
                var ge = new SegmentSetSelectedEvent(selectedSegments, count);
                project.projEventBus.post(ge);
                App.appEventBus.post(ge);
            }
            return;
        }
        if (!addToSelection) {
            clearSelection();
        }
        if (selectedSegments.contains(segment)) {
            selectedSegments.remove(segment);
            segment.setSelected(false);
            int count = selectedSegments.size();
            if (count == 0) {
                var e = new SegmentSelectedEvent(null, null, 0);
                project.projEventBus.post(e);
                App.appEventBus.post(e);
            } else if (count == 1) {
                Segment remaining = selectedSegments.iterator().next();
                var e = new SegmentSelectedEvent(remaining, remaining.getTrack(), 1);
                project.projEventBus.post(e);
                App.appEventBus.post(e);
            } else {
                var e = new SegmentSelectedEvent(null, null, count);
                project.projEventBus.post(e);
                App.appEventBus.post(e);
                var ge = new SegmentSetSelectedEvent(selectedSegments, count);
                project.projEventBus.post(ge);
                App.appEventBus.post(ge);
            }
        } else {
            selectedSegments.add(segment);
            segment.setSelected(true);
            int count = selectedSegments.size();
            if (count >= 2) {
                var e = new SegmentSelectedEvent(null, null, count);
                project.projEventBus.post(e);
                App.appEventBus.post(e);
                var ge = new SegmentSetSelectedEvent(selectedSegments, count);
                project.projEventBus.post(ge);
                App.appEventBus.post(ge);
            } else {
                var e = new SegmentSelectedEvent(segment, segment.getTrack(), 1);
                project.projEventBus.post(e);
                App.appEventBus.post(e);
            }
        }
    }

    public void clearSelection() {
        selectedSegments.setSelected(false);
        selectedSegments.clear();
        var e = new SegmentSelectedEvent(null, null, 0);
        project.projEventBus.post(e);
        App.appEventBus.post(e);
    }

    public void selectSegments(Collection<Segment> segments) {
        clearSelection();
        for (Segment seg : segments) {
            selectedSegments.add(seg);
            seg.setSelected(true);
        }
        int count = selectedSegments.size();
        if (count == 1) {
            Segment seg = selectedSegments.iterator().next();
            var e = new SegmentSelectedEvent(seg, seg.getTrack(), 1);
            project.projEventBus.post(e);
            App.appEventBus.post(e);
        } else if (count >= 2) {
            var e = new SegmentSelectedEvent(null, null, count);
            project.projEventBus.post(e);
            App.appEventBus.post(e);
            var ge = new SegmentSetSelectedEvent(selectedSegments, count);
            project.projEventBus.post(ge);
            App.appEventBus.post(ge);
        }
    }

    public SegmentSet selectedSegments() {
        return selectedSegments;
    }

    void groupSelectedSegments() {
        if (selectedSegments.size() < 2) return;

        boolean anyInGroup = false;
        for (Segment seg : selectedSegments) {
            if (seg.getGroup() != null) {
                anyInGroup = true;
                break;
            }
        }

        if (anyInGroup) {
            Map<Segment, SegmentGroup> savedState = new HashMap<>();
            Set<SegmentGroup> affectedGroups = new HashSet<>();
            for (Segment seg : selectedSegments) {
                SegmentGroup g = seg.getGroup();
                if (g != null) {
                    savedState.put(seg, g);
                    affectedGroups.add(g);
                }
            }
            Map<SegmentGroup, Set<Segment>> dissolvedMembers = new HashMap<>();
            for (SegmentGroup g : affectedGroups) {
                dissolvedMembers.put(g, new HashSet<>(g));
            }

            for (Segment seg : selectedSegments) {
                SegmentGroup g = seg.getGroup();
                if (g != null) {
                    g.remove(seg);
                }
            }
            for (SegmentGroup g : affectedGroups) {
                if (g.size() < 2) {
                    for (Segment s : new HashSet<>(g)) {
                        g.remove(s);
                    }
                }
            }

            project.undoManager.record(new UndoManager.UndoableCommand() {
                @Override
                public void undo() {
                    for (var e : dissolvedMembers.entrySet()) {
                        e.getKey().addAll(e.getValue());
                    }
                    for (var e : savedState.entrySet()) {
                        Segment seg = e.getKey();
                        SegmentGroup g = e.getValue();
                        if (g != null && !g.contains(seg)) {
                            g.add(seg);
                        }
                    }
                    dirty = true;
                }

                @Override
                public void redo() {
                    for (Segment seg : savedState.keySet()) {
                        SegmentGroup g = seg.getGroup();
                        if (g != null) {
                            g.remove(seg);
                        }
                    }
                    for (var e : dissolvedMembers.entrySet()) {
                        SegmentGroup g = e.getKey();
                        if (g.size() < 2) {
                            for (Segment s : new HashSet<>(g)) {
                                g.remove(s);
                            }
                        }
                    }
                    dirty = true;
                }
            });
        } else {
            SegmentGroup group = new SegmentGroup();
            List<Segment> segs = new ArrayList<>(selectedSegments);
            group.addAll(segs);

            project.undoManager.record(new UndoManager.UndoableCommand() {
                @Override
                public void undo() {
                    for (Segment seg : segs) {
                        group.remove(seg);
                    }
                    dirty = true;
                }

                @Override
                public void redo() {
                    group.addAll(segs);
                    dirty = true;
                }
            });
        }
    }
//FIXME:跨项目粘贴
    void performPaste() {
        var clip = App.copyManager.getClipboard();
        if (clip == null) return;

        Stage s = getStage();
        if (s == null) return;
        Vector2 local = stageToLocalCoordinates(
            s.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY())));

        long baseTime = Math.max(xToAbsoluteTime(local.x), 0);
        int baseTrack = Math.max(yToTrackIndex(local.y), 0);

        List<Segment> pasted;
        if (clip instanceof SegmentGroup templateGroup) {
            pasted = pasteGroup(templateGroup, baseTime, baseTrack);
        } else if (clip instanceof SegmentSet templateSet) {
            pasted = pasteSet(templateSet, baseTime, baseTrack);
        } else if (clip instanceof Segment template) {
            pasted = pasteSegment(template, baseTime, baseTrack);
        } else {
            pasted = List.of();
        }

        if (!pasted.isEmpty()) {
            selectSegments(pasted);
        }

        App.copyManager.refreshClipboard();
    }

    private List<Segment> pasteSegment(Segment template, long time, int baseTrack) {
        long duration = template.getRange().upperEndpoint() - template.getRange().lowerEndpoint();
        if (duration <= 0) return List.of();

        Track track = timeline.getTrack(baseTrack);
        var range = com.google.common.collect.Range.closedOpen(time, time + duration);
        int trackIndex = baseTrack;
        while (!track.isFree(range, Set.of())) {
            trackIndex++;
            track = timeline.getTrack(trackIndex);
            range = com.google.common.collect.Range.closedOpen(time, time + duration);
        }

        template.setOrigin(time + template.getOrigin() - template.getRange().lowerEndpoint());

        try (var h = timeline.record()) {
            timeline.tryAdd(track, template, Range.closedOpen(time, time + duration));
        }
        markTimelineDirty();
        return List.of(template);
    }

    private List<Segment> pasteGroup(SegmentGroup template, long baseTime, int baseTrack) {
        var pasted = new ArrayList<Segment>();

        List<Segment> sorted = new ArrayList<>(template);
        sorted.sort(java.util.Comparator.comparingInt(s -> s.getTrack().index));

        int minTrack = sorted.get(0).getTrack().index;
        long minStart = sorted.stream().mapToLong(s -> s.getRange().lowerEndpoint()).min().orElse(baseTime);
        long timeOffset = baseTime - minStart;

        try (var h = timeline.record()) {
            for (Segment seg : sorted) {
                long duration = seg.getRange().upperEndpoint() - seg.getRange().lowerEndpoint();
                if (duration <= 0) continue;

                int trackOffset = seg.getTrack().index - minTrack;
                int ti = baseTrack + trackOffset;
                Track track = timeline.getTrack(ti);
                long segStart = seg.getRange().lowerEndpoint() + timeOffset;
                var range = com.google.common.collect.Range.closedOpen(segStart, segStart + duration);
                while (!track.isFree(range, Set.of())) {
                    ti++;
                    track = timeline.getTrack(ti);
                    range = com.google.common.collect.Range.closedOpen(segStart, segStart + duration);
                }

                seg.setOrigin(seg.getOrigin() + timeOffset);

                timeline.tryAdd(track, seg, Range.closedOpen(segStart, segStart + duration));
                pasted.add(seg);
            }
        }

        markTimelineDirty();
        return pasted;
    }

    private List<Segment> pasteSet(SegmentSet template, long baseTime, int baseTrack) {
        var pasted = new ArrayList<Segment>();

        List<Segment> sorted = new ArrayList<>(template);
        sorted.sort(java.util.Comparator.comparingInt(s -> s.getTrack().index));

        int minTrack = sorted.get(0).getTrack().index;
        long minStart = sorted.stream().mapToLong(s -> s.getRange().lowerEndpoint()).min().orElse(baseTime);
        long timeOffset = baseTime - minStart;

        try (var h = timeline.record()) {
            for (Segment seg : sorted) {
                long duration = seg.getRange().upperEndpoint() - seg.getRange().lowerEndpoint();
                if (duration <= 0) continue;

                int trackOffset = seg.getTrack().index - minTrack;
                int ti = baseTrack + trackOffset;
                Track track = timeline.getTrack(ti);
                long segStart = seg.getRange().lowerEndpoint() + timeOffset;
                var range = com.google.common.collect.Range.closedOpen(segStart, segStart + duration);
                while (!track.isFree(range, Set.of())) {
                    ti++;
                    track = timeline.getTrack(ti);
                    range = com.google.common.collect.Range.closedOpen(segStart, segStart + duration);
                }

                seg.setOrigin(seg.getOrigin() + timeOffset);

                timeline.tryAdd(track, seg, Range.closedOpen(segStart, segStart + duration));
                pasted.add(seg);
            }
        }

        markTimelineDirty();
        return pasted;
    }

    protected void initDrag(SegActor actor, float x, float y) {
        dragHandler.initDrag(actor, x, y);
    }

    protected void segDrag(SegActor actor, float diffToActorX, float diffToActorY) {
        dragHandler.segDrag(actor, diffToActorX, diffToActorY);
    }

    protected void segDragEnd(SegActor actor) {
        dragHandler.segDragEnd(actor);
    }

    public void removeSeg(SegActor segActor) {
        dragHandler.removeSeg(segActor);
    }

    public void split(SegActor segActor, long time) {
        dragHandler.split(segActor, time);
    }

    int yToTrackIndex(float y) {
        final float top = getHeight() + view.trackYShift;
        final float distance = top - y;
        return (int) Math.floor(distance / view.trackHeight);
    }

    float trackIndexToTopY(int index) {
        return getHeight() + view.trackYShift - index * view.trackHeight;
    }

    @SuppressWarnings("unused")
    private float trackIndexToBottomY(int index) {
        return trackIndexToTopY(index) - view.trackHeight;
    }

    public Project getProject() {
        return project;
    }

    public Timeline getTimeline() {
        return timeline;
    }

    public void markTimelineDirty() {
        dirty = true;
    }

    @Override
    public void sizeChanged() {
        dirty = true;
    }

    class SegDragHandler {
        float firstX = Float.NaN, firstY = Float.NaN;
        private long dragOldStart;
        private long dragOldDuration;

        private List<Segment> dragMembers;
        private long[] dragOrigStarts;
        private long[] dragOrigDurations;
        private Track[] dragOrigTracks;

        void initDrag(SegActor actor, float diffToActorX, float diffToActorY) {
            var seg = actor.getSegment();
            var r = seg.getRange();
            dragOldStart = r.lowerEndpoint();
            dragOldDuration = r.upperEndpoint() - dragOldStart;
            firstX = diffToActorX;
            firstY = diffToActorY;
            timeline.record();
            initDragMembers(seg);
        }

        /** 拖拽中：每次鼠标移动 / act() 重建都会调用，按 DragSide 分派到三种分支。 */
        void segDrag(SegActor actor, float diffToActorX, float diffToActorY) {

            snapIndicatorTime = -1;

            var r = actor.getSegment().getRange();

            switch (actor.getDragSide()) {
                case FRONT: {
                    float upper = actor.getX() + actor.getWidth();
                    float target = actor.getX() + diffToActorX;
                    if (target >= upper) return;
                    target = Math.max(target, absoluteTimeToX(0));

                    long rawTime = xToAbsoluteTime(target);
                    long snapped = snapTime(rawTime, getSnapIgnoreSetForResize(actor.getSegment().getTrack()));
                    long appliedStart = Math.max(snapped, 0);
                    snapIndicatorTime = appliedStart != rawTime ? appliedStart : -1;

                    handleFrontResize(appliedStart);
                    break;
                }
                case BEHIND: {
                    if (diffToActorX < 1f) return;
                    float newWidth = diffToActorX;
                    float upper = actor.getX() + newWidth;

                    long rawUpper = xToAbsoluteTime(upper);
                    long snapped = snapTime(rawUpper, getSnapIgnoreSetForResize(actor.getSegment().getTrack()));
                    long appliedUpper = Math.max(snapped, 0);
                    snapIndicatorTime = appliedUpper != rawUpper ? appliedUpper : -1;
                    upper = absoluteTimeToX(appliedUpper);
                    newWidth = upper - actor.getX();
                    if (newWidth < 1f) return;

                    handleBehindResize(appliedUpper);
                    break;
                }
                case MIDDLE: {
                    // deltaX/deltaY 为相对按下点的累计位移，重复喂入同一坐标是安全的（幂等）
                    float deltaX = diffToActorX - firstX;
                    float deltaY = diffToActorY - firstY;
                    float targetX = actor.getX() + deltaX;
                    float targetY = actor.getY() + deltaY;

                    long duration = r.upperEndpoint() - r.lowerEndpoint();
                    long target = xToAbsoluteTime(targetX);

                    {
                        Set<Segment> ignore = getSnapIgnoreSet();
                        long segEnd = target + duration;
                        long snappedStart = snapTime(target, ignore);
                        long snappedEnd = snapTime(segEnd, ignore) - duration;
                        if (snappedEnd < 0) snappedEnd = 0;
                        boolean startMoved = snappedStart != target;
                        boolean endMoved = snappedEnd != target;
                        if (startMoved && endMoved) {
                            if (Math.abs(snappedStart - target) <= Math.abs(snappedEnd - target)) {
                                target = snappedStart;
                                snapIndicatorTime = snappedStart;
                            } else {
                                target = snappedEnd;
                                snapIndicatorTime = target + duration;
                            }
                        } else if (startMoved) {
                            target = snappedStart;
                            snapIndicatorTime = snappedStart;
                        } else if (endMoved) {
                            target = snappedEnd;
                            snapIndicatorTime = target + duration;
                        }
                        if (target < 0) target = 0;
                    }

                    var newTrack = timeline.getTrack(Math.max(0, yToTrackIndex(targetY + view.trackHeight / 2)));

                    handleMiddleDrag(target, newTrack, targetY);
                    for (Segment ms : dragMembers) {
                        SegActor ma = ms.getActor();
                        if (ma != null && ma.getParent() == TlGroup.this) ma.toFront();
                    }
                    break;
                }
            }
        }

        /** 收集参与拖拽的成员并快照各自的起点/时长/轨道。 */
        private void initDragMembers(Segment seg) {
            if (selectedSegments.size() > 1 && selectedSegments.contains(seg)) {
                dragMembers = new ArrayList<>(selectedSegments.size());
                dragMembers.add(seg);
                for (Segment s : selectedSegments) {
                    if (s != seg) dragMembers.add(s);
                }
            } else {
                dragMembers = new ArrayList<>(1);
                dragMembers.add(seg);
            }

            int n = dragMembers.size();
            dragOrigStarts = new long[n];
            dragOrigDurations = new long[n];
            dragOrigTracks = new Track[n];
            for (int i = 0; i < n; i++) {
                var sr = dragMembers.get(i).getRange();
                dragOrigStarts[i] = sr.lowerEndpoint();
                dragOrigDurations[i] = sr.upperEndpoint() - sr.lowerEndpoint();
                dragOrigTracks[i] = dragMembers.get(i).getTrack();
            }
        }

        private Set<Segment> getSnapIgnoreSet() {
            return new HashSet<>(dragMembers);
        }

        /** 裁切时的吸附忽略集：在 dragMembers 之外额外忽略同轨道所有片段 */
        private Set<Segment> getSnapIgnoreSetForResize(Track track) {
            Set<Segment> ignore = new HashSet<>(dragMembers);
            for (Segment s : track) {
                ignore.add(s);
            }
            return ignore;
        }

        private static final float SNAP_THRESHOLD_PX = 10f;

        /** 在 [time±threshold] 内扫描所有轨道片段，找到最近的起点/终点作为吸附目标。 */
        private long snapTime(long time, Set<Segment> ignore) {
            if (App.shortcutManager.isActive(Actions.SNAP_IGNORE)) {
                return time;
            }
            long threshold = Math.max(1, (long) (SNAP_THRESHOLD_PX / getWidth() * view.durationTime));
            long best = time;
            long bestDist = threshold;

            long searchStart = Math.max(0, time - threshold);
            long searchEnd = time + threshold;
            if (searchEnd <= searchStart) return time;

            Range<Long> searchRange = Range.closedOpen(searchStart, searchEnd);

            for (int i = 0; i < timeline.getTracks().size(); i++) {
                Track track = timeline.getTracks().get(i);
                for (var entry : track.getSubRangeMapAsEntrySet(searchRange)) {
                    if (ignore.contains(entry.getValue())) continue;
                    Range<Long> r = entry.getKey();

                    long dist = Math.abs(r.lowerEndpoint() - time);
                    if (dist < bestDist) {
                        best = r.lowerEndpoint();
                        bestDist = dist;
                    }

                    dist = Math.abs(r.upperEndpoint() - time);
                    if (dist < bestDist) {
                        best = r.upperEndpoint();
                        bestDist = dist;
                    }
                }
            }

            if (time < threshold && time < bestDist) {
                best = 0;
            }

            return best;
        }

        // 整体平移。模型 move/setStart/setEnd 都按相对当前位置位移，因此这里的
        // deltaTime/deltaTrack 必须相对当前模型状态，避免重建重放反复累加。
        private void handleMiddleDrag(long target, Track newTrack, float targetY) {
            List<Segment> members = List.copyOf(dragMembers);

            int trackDelta = newTrack.index - members.get(0).getTrack().index;

            int minIdx = Integer.MAX_VALUE;
            for (Segment m : members) minIdx = Math.min(minIdx, m.getTrack().index);
            if (minIdx + trackDelta < 0) return;

            float mousePx = absoluteTimeToX(target);

            long currentStart0 = members.get(0).getRange().lowerEndpoint();
            long appliedDelta = target - currentStart0;

            for (int tries = 0; tries < 6; tries++) {
                long fix = timeline.move(members, appliedDelta, trackDelta);
                if (fix == 0) {
                    for (int i = 0; i < members.size(); i++) {
                        Segment ms = dragMembers.get(i);
                        SegActor msActor = ms.getActor();
                        float y = i == 0
                            ? targetY
                            : targetY - (dragOrigTracks[i].index - dragOrigTracks[0].index) * view.trackHeight;
                        long st = ms.getRange().lowerEndpoint();
                        long en = ms.getRange().upperEndpoint();
                        msActor.setPosition(absoluteTimeToX(st), y);
                        msActor.setSize(absoluteTimeToX(en) - absoluteTimeToX(st), view.trackHeight);
                    }
                    return;
                }
                long cand = appliedDelta + fix;
                long candStart0 = members.get(0).getRange().lowerEndpoint() + cand;
                float candPx = absoluteTimeToX(candStart0);
                if (Math.abs(candPx - mousePx) > 200f) return;
                appliedDelta = cand;
                snapIndicatorTime = candStart0;
            }
        }

        private void handleFrontResize(long newStart) {
            long absDelta = newStart - dragOldStart;
            int n = dragMembers.size();
            for (int i = 0; i < n; i++) {
                long ns = dragOrigStarts[i] + absDelta;
                if (ns >= dragOrigStarts[i] + dragOrigDurations[i] || ns < 0) return;
            }

            List<Segment> members = List.copyOf(dragMembers);
            float mousePx = absoluteTimeToX(newStart);

            long currentStart0 = members.get(0).getRange().lowerEndpoint();
            long appliedDelta = newStart - currentStart0;

            for (int tries = 0; tries < 6; tries++) {
                long fix = timeline.setStart(members, appliedDelta);
                if (fix == 0) {
                    for (int i = 0; i < n; i++) {
                        Segment ms = dragMembers.get(i);
                        long ns = ms.getRange().lowerEndpoint();
                        long oe = ms.getRange().upperEndpoint();
                        SegActor msActor = ms.getActor();
                        msActor.setX(absoluteTimeToX(ns));
                        msActor.setWidth(absoluteTimeToX(oe) - absoluteTimeToX(ns));
                    }
                    return;
                }
                long cand = appliedDelta + fix;
                long candStart0 = members.get(0).getRange().lowerEndpoint() + cand;
                float candPx = absoluteTimeToX(candStart0);
                if (candPx - mousePx > 200f) return;
                if (candStart0 >= members.get(0).getRange().upperEndpoint() || candStart0 < 0) return;
                appliedDelta = cand;
            }
        }

        private void handleBehindResize(long newEnd) {
            long oldEnd = dragOldStart + dragOldDuration;
            long absDelta = newEnd - oldEnd;
            int n = dragMembers.size();
            for (int i = 0; i < n; i++) {
                long ne = dragOrigStarts[i] + dragOrigDurations[i] + absDelta;
                if (ne <= dragOrigStarts[i]) return;
            }

            List<Segment> members = List.copyOf(dragMembers);
            float mousePx = absoluteTimeToX(newEnd);

            long currentEnd0 = members.get(0).getRange().upperEndpoint();
            long appliedDelta = newEnd - currentEnd0;

            for (int tries = 0; tries < 6; tries++) {
                long fix = timeline.setEnd(members, appliedDelta);
                if (fix == 0) {
                    for (int i = 0; i < n; i++) {
                        Segment ms = dragMembers.get(i);
                        SegActor msActor = ms.getActor();
                        msActor.setWidth(
                            absoluteTimeToX(ms.getRange().upperEndpoint())
                                - absoluteTimeToX(ms.getRange().lowerEndpoint()));
                    }
                    return;
                }
                long cand = appliedDelta + fix;
                long candEnd0 = members.get(0).getRange().upperEndpoint() + cand;
                float candPx = absoluteTimeToX(candEnd0);
                if (mousePx - candPx > 200f) return;
                if (candEnd0 <= members.get(0).getRange().lowerEndpoint()) return;
                appliedDelta = cand;
            }
        }

        void segDragEnd(SegActor actor) {
            dirty = true;
            snapIndicatorTime = -1;

            timeline.submit();

            dragMembers = null;
            dragOrigStarts = null;
            dragOrigDurations = null;
            dragOrigTracks = null;
        }

    void removeSeg(SegActor segActor) {
            removeActor(segActor);
            Segment s = segActor.getSegment();
            try (var h = timeline.record()) {
                timeline.remove(s);
            }
            dirty = true;
        }

        /** 右键菜单"分割"入口 */
        void split(SegActor segActor, long time) {
            splitSegment(segActor.getSegment(), time);
            dirty = true;
        }

        /** 快捷键分割入口：按当前鼠标位置定位分割点 */
        void splitAtCursor() {
            Stage s = getStage();
            if (s == null) return;
            Vector2 local = stageToLocalCoordinates(
                s.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY())));
            int trackIndex = yToTrackIndex(local.y);
            long time = xToAbsoluteTime(local.x);
            Track track = timeline.getTrack(trackIndex);
            var entry = track.getEntry(time);
            if (entry == null) return;
            splitSegment(entry.getValue(), time);
            dirty = true;
        }

        private void splitSegment(Segment seg, long time) {
            SegmentGroup group = seg.getGroup();
            var segs = group != null ? List.copyOf(group) : List.of(seg);
            List<Segment> beforeSegs = new ArrayList<>();
            List<Segment> afterSegs = new ArrayList<>();
            boolean splitAny = false;
            try (var h = timeline.record()) {
                for (Segment member : segs) {
                    var r = member.getRange();
                    long start = r.lowerEndpoint();
                    long end = r.upperEndpoint();
                    if (time > start && time < end) {
                        Track track = member.getTrack();
                        timeline.split(track, time);
                        beforeSegs.add(member);
                        afterSegs.add(track.getEntry(time).getValue());
                        splitAny = true;
                    } else if (end <= time) {
                        beforeSegs.add(member);
                    } else {
                        afterSegs.add(member);
                    }
                }
            }
            if (splitAny && group != null) {
                for (Segment member : segs) {
                    group.remove(member);
                }
                if (beforeSegs.size() >= 2) {
                    regroup(beforeSegs);
                }
                if (afterSegs.size() >= 2) {
                    regroup(afterSegs);
                }
            }
        }

        private static SegmentGroup regroup(Collection<Segment> members) {
            var g = new SegmentGroup();
            g.addAll(members);
            return g;
        }

        private void deleteAtCursor() {
            Stage s = getStage();
            if (s == null) return;
            Vector2 local = stageToLocalCoordinates(
                s.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY())));
            int trackIndex = yToTrackIndex(local.y);
            Track track = timeline.getTrack(trackIndex);
            var entry = track.getEntry(xToAbsoluteTime(local.x));
            if (entry == null) return;
            var seg = entry.getValue();
            try (var h = timeline.record()) {
                timeline.remove(seg);
            }
            dirty = true;
        }

        void deleteSelected() {
            if (selectedSegments.isEmpty()) {
                deleteAtCursor();
                return;
            }

            var segs = List.copyOf(selectedSegments);

            clearSelection();

            try (var h = timeline.record()) {
                timeline.remove(segs);
            }
            dirty = true;
        }


    }

    class TimelineRenderer {
        final ShapeDrawer shapeDrawer = App.root.getShapeDrawer();
        private static final float PIXELS_PER_TICK = 200f;
        private static final Color background = new Color(0.08f, 0.08f, 0.08f, 1f);
        private static final Color contentArea = new Color(1f,1f,1f, 0.05f);
        private static final Color trackBand   = new Color(1f,1f,1f, 0.03f);
        private static final Color tickLine = new Color(1f,1f,1f, 0.06f);

        void drawBackground() {
            shapeDrawer.filledRectangle(0, 0, getWidth(), getHeight(), background);

            final float startX = absoluteTimeToX(0);
            final float endX = absoluteTimeToX(timeline.getLength());

            shapeDrawer.filledRectangle(startX, 0, endX - startX, getHeight(), contentArea);
        }

        void drawTicks() {
            final long interval = niceScale((long) (view.durationTime * PIXELS_PER_TICK / getWidth()));
            final long start = (view.startTime / interval) * interval;

            for (long t = start; t < view.startTime + view.durationTime; t += interval) {
                float x = absoluteTimeToX(t);
                shapeDrawer.filledRectangle(x, 0, 1, getHeight(), tickLine);
            }
        }

        void drawTrackBands() {
            final float top = getHeight() + view.trackYShift;
            for (int i = 0; ; i += 2) {
                final float y = top - i * view.trackHeight;
                if (y <= -view.trackHeight) break;
                shapeDrawer.filledRectangle(0, y - view.trackHeight, getWidth(), view.trackHeight, trackBand);
            }
        }

        void drawPlayhead() {
            final float x = absoluteTimeToX(playhead.getTime());

            shapeDrawer.filledTriangle(
                x - 10, getHeight(),
                x + 10, getHeight(),
                x, getHeight() - 20,
                Color.RED
            );

            shapeDrawer.line(x, 0, x, getHeight(), Color.RED, 3);
        }

        void drawSnapIndicator() {
            final float x = absoluteTimeToX(snapIndicatorTime);
            shapeDrawer.line(x, 0, x, getHeight(), Colors.SNAP_GUIDE, 2);
        }
    }

    public enum Actions implements ShortcutAction {
        SCROLL_LEFT("向左滚动", A),
        SCROLL_RIGHT("向右滚动", D),
        SCROLL_UP("向上滚动", W),
        SCROLL_DOWN("向下滚动", S),
        SPLIT("分割", Q),
        DELETE("删除", X),
        UNDO("撤销", CONTROL_LEFT, Z),
        REDO("重做", CONTROL_LEFT, Y),
        PLAY_PAUSE("播放/暂停", SPACE),
        GROUP("分组", F),
        MARQUEE_SELECT("框选", CONTROL_LEFT),
        SEEK("定位播放头", E),
        SNAP_IGNORE("忽略吸附", CONTROL_LEFT),
        COPY("复制", CONTROL_LEFT, C),
        PASTE("粘贴", CONTROL_LEFT, V);

        private final String displayName;
        private final int[] defaultKeys;

        Actions(String displayName, int... defaultKeys) {
            this.displayName = displayName;
            this.defaultKeys = defaultKeys;
        }

        @Override
        public String displayName() {
            return displayName;
        }

        @Override
        public int[] defaultKeys() {
            return defaultKeys.clone();
        }
    }

    static class ViewState {
        long startTime;
        long durationTime;
        float trackHeight;
        float trackYShift;

        float timeToX(long time, float width) {
            return (float) (time - startTime) / durationTime * width;
        }

        long xToTime(float x, float width) {
            return startTime + (long) ((x / width) * durationTime);
        }

        Range<Long> visibleRange() {
            return Range.closedOpen(startTime, startTime + durationTime);
        }

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

        void scrollHorizontal(float deltaPixels, float width) {
            startTime = Math.max(startTime + (xToTime(deltaPixels, width) - xToTime(0, width)), 0);
        }

        void scrollVertical(float delta) {
            trackYShift = Math.max(0, trackYShift + delta);
        }

        void adjustTrackHeight(float delta) {
            trackHeight = Math.max(trackHeight + delta, 10);
        }
    }
}
