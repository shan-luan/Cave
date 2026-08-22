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

import org.jspecify.annotations.NonNull;

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

        if (dirty) {
            dragHandler.beginCommit();
            clearChildren(false);

            var visibleRange = view.visibleRange();
            for (int i = timeline.getTracks().size() - 1; i >= 0; i--) {
                final Track track = timeline.getTracks().get(i);

                for (var entry : track.getSubRangeMapAsEntrySet(visibleRange)) {
                    SegActor actor = entry.getValue().getActor();
                    var r = actor.getSegment().getRange();
                    switch (actor.getDragSide()) {
                        case FRONT,BEHIND:
                            actor.setPosition(
                                absoluteTimeToX(r.lowerEndpoint()),
                                getHeight() + view.trackYShift - (i + 1) * view.trackHeight
                            );
                            Stage s = getStage();
                            dragHandler.segDrag(actor, stageToLocalCoordinates(s.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY()))).x - actor.getX(), Float.NaN);
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
                            dragHandler.segDrag(actor, local.x - actor.getX(), local.y - actor.getY());
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

            dragHandler.endCommit();
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
                for (Segment s : group.getSegments()) {
                    if (selectedSegments.contains(s)) { anySelected = true; break; }
                }
                if (anySelected) {
                    for (Segment s : group.getSegments()) {
                        selectedSegments.remove(s);
                        s.setSelected(false);
                    }
                } else {
                    for (Segment s : group.getSegments()) {
                        selectedSegments.add(s);
                        s.setSelected(true);
                    }
                }
            } else {
                clearSelection();
                for (Segment s : group.getSegments()) {
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
                Segment remaining = selectedSegments.getSegments().iterator().next();
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
                Segment remaining = selectedSegments.getSegments().iterator().next();
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
            Segment seg = selectedSegments.getSegments().iterator().next();
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
        for (Segment seg : selectedSegments.getSegments()) {
            if (seg.getGroup() != null) {
                anyInGroup = true;
                break;
            }
        }

        if (anyInGroup) {
            Map<Segment, SegmentGroup> savedState = new HashMap<>();
            Set<SegmentGroup> affectedGroups = new HashSet<>();
            for (Segment seg : selectedSegments.getSegments()) {
                SegmentGroup g = seg.getGroup();
                if (g != null) {
                    savedState.put(seg, g);
                    affectedGroups.add(g);
                }
            }
            Map<SegmentGroup, Set<Segment>> dissolvedMembers = new HashMap<>();
            for (SegmentGroup g : affectedGroups) {
                dissolvedMembers.put(g, new HashSet<>(g.getSegments()));
            }

            for (Segment seg : selectedSegments.getSegments()) {
                SegmentGroup g = seg.getGroup();
                if (g != null) {
                    g.remove(seg);
                }
            }
            for (SegmentGroup g : affectedGroups) {
                if (g.size() < 2) {
                    for (Segment s : new HashSet<>(g.getSegments())) {
                        g.remove(s);
                    }
                }
            }

            project.undoManager.record(new UndoManager.UndoableCommand() {
                @Override
                public void undo() {
                    for (var e : dissolvedMembers.entrySet()) {
                        SegmentGroup g = e.getKey();
                        for (Segment s : e.getValue()) {
                            g.add(s);
                        }
                    }
                    for (var e : savedState.entrySet()) {
                        Segment seg = e.getKey();
                        SegmentGroup g = e.getValue();
                        if (g != null && !g.getSegments().contains(seg)) {
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
                            for (Segment s : new HashSet<>(g.getSegments())) {
                                g.remove(s);
                            }
                        }
                    }
                    dirty = true;
                }
            });
        } else {
            SegmentGroup group = new SegmentGroup();
            List<Segment> segs = new ArrayList<>(selectedSegments.getSegments());

            for (Segment seg : segs) {
                group.add(seg);
            }

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
                    for (Segment seg : segs) {
                        group.add(seg);
                    }
                    dirty = true;
                }
            });
        }
    }

    //FIXME:跨项目粘贴的资源问题
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

        timeline.add(track, template, time, duration);
        project.undoManager.record(new UndoManager.AddSegCommand(track, template, time, duration));
        markTimelineDirty();
        return List.of(template);
    }

    private List<Segment> pasteGroup(SegmentGroup template, long baseTime, int baseTrack) {
        var cmds = new ArrayList<UndoManager.UndoableCommand>();
        var pasted = new ArrayList<Segment>();

        List<Segment> sorted = new ArrayList<>(template.getSegments());
        sorted.sort(java.util.Comparator.comparingInt(s -> s.getTrack().index));

        int minTrack = sorted.get(0).getTrack().index;
        long minStart = sorted.stream().mapToLong(s -> s.getRange().lowerEndpoint()).min().orElse(baseTime);
        long timeOffset = baseTime - minStart;

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

            timeline.add(track, seg, segStart, duration);
            cmds.add(new UndoManager.AddSegCommand(track, seg, segStart, duration));
            pasted.add(seg);
        }

        if (!cmds.isEmpty()) {
            project.undoManager.record(new UndoManager.CompoundCommand(
                cmds.toArray(new UndoManager.UndoableCommand[0])));
        }

        markTimelineDirty();
        return pasted;
    }

    private List<Segment> pasteSet(SegmentSet template, long baseTime, int baseTrack) {
        var cmds = new ArrayList<UndoManager.UndoableCommand>();
        var pasted = new ArrayList<Segment>();

        List<Segment> sorted = new ArrayList<>(template.getSegments());
        sorted.sort(java.util.Comparator.comparingInt(s -> s.getTrack().index));

        int minTrack = sorted.get(0).getTrack().index;
        long minStart = sorted.stream().mapToLong(s -> s.getRange().lowerEndpoint()).min().orElse(baseTime);
        long timeOffset = baseTime - minStart;

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

            timeline.add(track, seg, segStart, duration);
            cmds.add(new UndoManager.AddSegCommand(track, seg, segStart, duration));
            pasted.add(seg);
        }

        if (!cmds.isEmpty()) {
            project.undoManager.record(new UndoManager.CompoundCommand(
                cmds.toArray(new UndoManager.UndoableCommand[0])));
        }

        markTimelineDirty();
        return pasted;
    }

    // -- 委托给 SegDragHandler --

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

    // -------------------------------------------------------------------------
    // 内部类：片段拖拽处理器
    // -------------------------------------------------------------------------

class SegDragHandler {
        float firstX = Float.NaN, firstY = Float.NaN;
        private long dragOldStart;
        private long dragOldDuration;
        private Track dragOldTrack;

        // 模型变更先推迟到本帧迭代完成后再应用，避免同一线程上的变更破坏迭代。
        private boolean committing;
        private final @NonNull ArrayList<Runnable> deferredMutations = new ArrayList<>();

        private void applyMutation(Runnable r) {
            if (committing) {
                deferredMutations.add(r);
            } else {
                r.run();
            }
        }

        void beginCommit() {
            committing = true;
        }

        void endCommit() {
            committing = false;
            for (Runnable r : deferredMutations) r.run();
            deferredMutations.clear();
        }

        private List<Segment> dragMembers;
        private long[] dragOrigStarts;
        private long[] dragOrigDurations;
        private Track[] dragOrigTracks;

        void initDrag(SegActor actor, float diffToActorX, float diffToActorY) {
            var seg = actor.getSegment();
            var r = seg.getRange();
            dragOldStart = r.lowerEndpoint();
            dragOldDuration = r.upperEndpoint() - dragOldStart;
            dragOldTrack = seg.getTrack();
            firstX = diffToActorX;
            firstY = diffToActorY;
            initDragMembers(seg);
        }

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
                    long snapped = snapTime(rawTime, getSnapIgnoreSet());
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
                    long snapped = snapTime(rawUpper, getSnapIgnoreSet());
                    long appliedUpper = Math.max(snapped, 0);
                    snapIndicatorTime = appliedUpper != rawUpper ? appliedUpper : -1;
                    upper = absoluteTimeToX(appliedUpper);
                    newWidth = upper - actor.getX();
                    if (newWidth < 1f) return;

                    handleBehindResize(appliedUpper);
                    break;
                }
                case MIDDLE: {
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
                        targetX = absoluteTimeToX(target);
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

        private void initDragMembers(Segment seg) {
            Set<Segment> selSegs = selectedSegments.getSegments();
            if (selSegs.size() > 1 && selSegs.contains(seg)) {
                dragMembers = new ArrayList<>(selSegs.size());
                dragMembers.add(seg);
                for (Segment s : selSegs) {
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

        private static final float SNAP_THRESHOLD_PX = 10f;

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

        private void handleMiddleDrag(long target, Track newTrack, float targetY) {

            long timeDelta = target - dragOldStart;
            int trackDelta = newTrack.index - dragOldTrack.index;

            int n = dragMembers.size();

            long[] newStarts = new long[n];
            Track[] newTracks = new Track[n];
            for (int i = 0; i < n; i++) {
                long msTarget = dragOrigStarts[i] + timeDelta;
                int targetTrackIdx = dragOrigTracks[i].index + trackDelta;
                if (targetTrackIdx < 0 || msTarget < 0) {
                    return;
                }
                newStarts[i] = msTarget;
                newTracks[i] = timeline.getTrack(targetTrackIdx);
            }

            boolean canMove = timeline.canMoveGroup(dragMembers, newStarts, dragOrigDurations, newTracks);

            if (!canMove) {
                long rightMinDelta = Long.MIN_VALUE;
                long leftMaxDelta = Long.MAX_VALUE;
                Set<Segment> ignore = new HashSet<>(dragMembers);

                for (int i = 0; i < n; i++) {
                    Track tr = newTracks[i];
                    for (var occ : tr.getSubRangeMapAsEntrySet(
                            Range.closedOpen(newStarts[i], newStarts[i] + dragOrigDurations[i]))) {
                        if (ignore.contains(occ.getValue())) continue;
                        rightMinDelta = Math.max(rightMinDelta,
                            occ.getKey().upperEndpoint() - dragOrigStarts[i]);
                        leftMaxDelta = Math.min(leftMaxDelta,
                            occ.getKey().lowerEndpoint() - dragOrigDurations[i] - dragOrigStarts[i]);
                    }
                }

                long snappedTarget = -1;
                long snappedIndicator = -1;

                float mousePx = absoluteTimeToX(target);

                if (rightMinDelta > timeDelta) {
                    long rightTarget = dragOldStart + rightMinDelta;
                    float snapPx = absoluteTimeToX(rightTarget);
                    if (snapPx - mousePx <= 200f) {
                        long[] rightStarts = new long[n];
                        Track[] rightTracks = new Track[n];
                        boolean ok = true;
                        for (int i = 0; i < n; i++) {
                            rightStarts[i] = dragOrigStarts[i] + rightMinDelta;
                            int idx = dragOrigTracks[i].index + trackDelta;
                            if (idx < 0 || rightStarts[i] < 0) { ok = false; break; }
                            rightTracks[i] = timeline.getTrack(idx);
                        }
                        if (ok && timeline.canMoveGroup(dragMembers, rightStarts, dragOrigDurations, rightTracks)) {
                            snappedTarget = rightTarget;
                            snappedIndicator = rightTarget;
                        }
                    }
                }

                if (leftMaxDelta < timeDelta && leftMaxDelta >= 0) {
                    long leftTarget = dragOldStart + leftMaxDelta;
                    float snapPx = absoluteTimeToX(leftTarget);
                    if (mousePx - snapPx <= 200f) {
                        long[] leftStarts = new long[n];
                        Track[] leftTracks = new Track[n];
                        boolean ok = true;
                        for (int i = 0; i < n; i++) {
                            leftStarts[i] = dragOrigStarts[i] + leftMaxDelta;
                            int idx = dragOrigTracks[i].index + trackDelta;
                            if (idx < 0) { ok = false; break; }
                            leftTracks[i] = timeline.getTrack(idx);
                        }
                        if (ok && timeline.canMoveGroup(dragMembers, leftStarts, dragOrigDurations, leftTracks)) {
                            if (snappedTarget < 0 || Math.abs(leftTarget - target) < Math.abs(snappedTarget - target)) {
                                snappedTarget = leftTarget;
                                snappedIndicator = leftTarget + dragOrigDurations[0];
                            }
                        }
                    }
                }

                if (snappedTarget >= 0) {
                    target = snappedTarget;
                    if (snappedIndicator >= 0) snapIndicatorTime = snappedIndicator;
                    timeDelta = target - dragOldStart;
                    for (int i = 0; i < n; i++) {
                        newStarts[i] = dragOrigStarts[i] + timeDelta;
                        int idx = dragOrigTracks[i].index + trackDelta;
                        newTracks[i] = timeline.getTrack(idx);
                    }
                    canMove = true;
                }
            }

            if (canMove) {
                final long[] prevStarts = new long[n];
                final Track[] prevTracks = new Track[n];
                final long[] nStarts = newStarts;
                final Track[] nTracks = newTracks;
                final long[] nDurations = dragOrigDurations;
                applyMutation(() -> {
                    for (int i = 0; i < dragMembers.size(); i++) {
                        Segment ms2 = dragMembers.get(i);
                        var r = ms2.getRange();
                        prevStarts[i] = r.lowerEndpoint();
                        prevTracks[i] = ms2.getTrack();
                        timeline.remove(prevTracks[i], Range.closedOpen(prevStarts[i], r.upperEndpoint()));
                    }
                    for (int i = 0; i < dragMembers.size(); i++) {
                        Segment ms2 = dragMembers.get(i);
                        timeline.add(nTracks[i], ms2, nStarts[i], nDurations[i]);
                        ms2.offsetOrigin(nStarts[i] - prevStarts[i]);
                    }
                });
            }

            for (int i = 0; i < n; i++) {
                Segment ms = dragMembers.get(i);
                SegActor msActor = ms.getActor();
                float y = i == 0
                    ? targetY
                    : targetY - (dragOrigTracks[i].index - dragOrigTracks[0].index) * view.trackHeight;
                msActor.setPosition(
                    absoluteTimeToX(newStarts[i]),
                    y
                );
                msActor.setSize(
                    absoluteTimeToX(newStarts[i] + dragOrigDurations[i]) - absoluteTimeToX(newStarts[i]),
                    view.trackHeight
                );
            }

        }

        private void handleFrontResize(long newStart) {
            long timeDelta = newStart - dragOldStart;
            int n = dragMembers.size();

            long[] newStarts = new long[n];
            long[] newDurations = new long[n];
            for (int i = 0; i < n; i++) {
                long msNewStart = dragOrigStarts[i] + timeDelta;
                long msOldEnd = dragOrigStarts[i] + dragOrigDurations[i];
                if (msNewStart >= msOldEnd || msNewStart < 0) {
                    return;
                }
                newStarts[i] = msNewStart;
                newDurations[i] = msOldEnd - msNewStart;
            }

            if (!timeline.canMoveGroup(dragMembers, newStarts, newDurations, dragOrigTracks)) {
                long rightMinDelta = Long.MIN_VALUE;
                Set<Segment> ignore = new HashSet<>(dragMembers);
                for (int i = 0; i < n; i++) {
                    Track tr = dragOrigTracks[i];
                    for (var occ : tr.getSubRangeMapAsEntrySet(
                            Range.closedOpen(newStarts[i], newStarts[i] + newDurations[i]))) {
                        if (ignore.contains(occ.getValue())) continue;
                        rightMinDelta = Math.max(rightMinDelta,
                            occ.getKey().upperEndpoint() - dragOrigStarts[i]);
                    }
                }
                if (rightMinDelta > timeDelta) {
                    boolean ok = true;
                    for (int i = 0; i < n; i++) {
                        long snappedStart = dragOrigStarts[i] + rightMinDelta;
                        long snappedEnd = dragOrigStarts[i] + dragOrigDurations[i];
                        if (snappedStart >= snappedEnd || snappedStart < 0) { ok = false; break; }
                        newStarts[i] = snappedStart;
                        newDurations[i] = snappedEnd - snappedStart;
                    }
                    if (!ok || !timeline.canMoveGroup(dragMembers, newStarts, newDurations, dragOrigTracks)) {
                        return;
                    }
                    snapIndicatorTime = dragOrigStarts[0] + rightMinDelta;
                } else {
                    return;
                }
            }

            final long[] fNewStarts = newStarts;
            final long[] fOldEnds = new long[n];
            for (int i = 0; i < n; i++) fOldEnds[i] = dragOrigStarts[i] + dragOrigDurations[i];
            final Track[] fTracks = dragOrigTracks;
            applyMutation(() -> {
                for (int i = 0; i < dragMembers.size(); i++) {
                    Segment ms2 = dragMembers.get(i);
                    var rr = ms2.getRange();
                    timeline.remove(ms2.getTrack(),
                        Range.closedOpen(rr.lowerEndpoint(), rr.upperEndpoint()));
                }
                for (int i = 0; i < dragMembers.size(); i++) {
                    Segment ms2 = dragMembers.get(i);
                    long msNewStart = fNewStarts[i];
                    timeline.add(fTracks[i], ms2, msNewStart, fOldEnds[i] - msNewStart);
                }
            });

            for (int i = 0; i < n; i++) {
                Segment ms = dragMembers.get(i);
                long msNewStart = newStarts[i];
                long msOldEnd = dragOrigStarts[i] + dragOrigDurations[i];
                SegActor msActor = ms.getActor();
                msActor.setX(absoluteTimeToX(msNewStart));
                msActor.setWidth(absoluteTimeToX(msOldEnd) - absoluteTimeToX(msNewStart));
            }

        }

        private void handleBehindResize(long newEnd) {
            long oldEnd = dragOldStart + dragOldDuration;
            long timeDelta = newEnd - oldEnd;
            int n = dragMembers.size();

            long[] newDurations = new long[n];
            for (int i = 0; i < n; i++) {
                long msOldEnd = dragOrigStarts[i] + dragOrigDurations[i];
                long msNewEnd = msOldEnd + timeDelta;
                if (msNewEnd <= dragOrigStarts[i]) {
                    return;
                }
                newDurations[i] = msNewEnd - dragOrigStarts[i];
            }

            if (!timeline.canMoveGroup(dragMembers, dragOrigStarts, newDurations, dragOrigTracks)) {
                long leftMaxDelta = Long.MAX_VALUE;
                Set<Segment> ignore = new HashSet<>(dragMembers);
                for (int i = 0; i < n; i++) {
                    Track tr = dragOrigTracks[i];
                    for (var occ : tr.getSubRangeMapAsEntrySet(
                            Range.closedOpen(dragOrigStarts[i], dragOrigStarts[i] + newDurations[i]))) {
                        if (ignore.contains(occ.getValue())) continue;
                        leftMaxDelta = Math.min(leftMaxDelta,
                            occ.getKey().lowerEndpoint() - dragOrigDurations[i] - dragOrigStarts[i]);
                    }
                }
                if (leftMaxDelta < timeDelta && leftMaxDelta >= 0) {
                    boolean ok = true;
                    for (int i = 0; i < n; i++) {
                        long msNewEnd = dragOrigStarts[i] + dragOrigDurations[i] + leftMaxDelta;
                        if (msNewEnd <= dragOrigStarts[i]) { ok = false; break; }
                        newDurations[i] = msNewEnd - dragOrigStarts[i];
                    }
                    if (!ok || !timeline.canMoveGroup(dragMembers, dragOrigStarts, newDurations, dragOrigTracks)) {
                        return;
                    }
                    snapIndicatorTime = dragOrigStarts[0] + dragOrigDurations[0] + leftMaxDelta;
                } else {
                    return;
                }
            }

            final long[] fNewDurations = newDurations;
            final long[] fOrigStarts = dragOrigStarts;
            final Track[] fTracks2 = dragOrigTracks;
            applyMutation(() -> {
                for (int i = 0; i < dragMembers.size(); i++) {
                    Segment ms2 = dragMembers.get(i);
                    var r2 = ms2.getRange();
                    timeline.remove(ms2.getTrack(),
                        Range.closedOpen(r2.lowerEndpoint(), r2.upperEndpoint()));
                }
                for (int i = 0; i < dragMembers.size(); i++) {
                    Segment ms2 = dragMembers.get(i);
                    timeline.add(fTracks2[i], ms2, fOrigStarts[i], fNewDurations[i]);
                }
            });

            for (int i = 0; i < n; i++) {
                SegActor msActor = dragMembers.get(i).getActor();
                msActor.setWidth(
                    absoluteTimeToX(dragOrigStarts[i] + newDurations[i]) - absoluteTimeToX(dragOrigStarts[i]));
            }

        }

        void segDragEnd(SegActor actor) {
            dirty = true;
            snapIndicatorTime = -1;

            var cmds = getUndoableCommands(actor);
            if (!cmds.isEmpty()) {
                project.undoManager.record(
                    new UndoManager.CompoundCommand(cmds.toArray(new UndoManager.UndoableCommand[0])));
            }

            dragMembers = null;
            dragOrigStarts = null;
            dragOrigDurations = null;
            dragOrigTracks = null;
        }

    private @NonNull ArrayList<UndoManager.UndoableCommand> getUndoableCommands(SegActor actor) {
        int n = dragMembers.size();
        var cmds = new ArrayList<UndoManager.UndoableCommand>();
        if (actor.getDragSide() == DragSide.MIDDLE) {
            for (int i = 0; i < n; i++) {
                Segment ms = dragMembers.get(i);
                var msr = ms.getRange();
                long newStart = msr.lowerEndpoint();
                long newDuration = msr.upperEndpoint() - newStart;
                Track newTrack = ms.getTrack();

                if (dragOrigStarts[i] != newStart
                    || dragOrigDurations[i] != newDuration
                    || dragOrigTracks[i] != newTrack) {
                    cmds.add(new UndoManager.MoveSegCommand(
                        dragOrigTracks[i], newTrack, ms,
                        dragOrigStarts[i], dragOrigDurations[i], newStart, newDuration));
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                Segment ms = dragMembers.get(i);
                var msr = ms.getRange();
                long newStart = msr.lowerEndpoint();
                long newDuration = msr.upperEndpoint() - newStart;

                if (dragOrigStarts[i] != newStart
                    || dragOrigDurations[i] != newDuration) {
                    cmds.add(new UndoManager.ResizeSegCommand(
                        dragOrigTracks[i], ms,
                        dragOrigStarts[i], dragOrigDurations[i], newStart, newDuration));
                }
            }
        }
        return cmds;
    }

    void removeSeg(SegActor segActor) {
            removeActor(segActor);
            Segment s = segActor.getSegment();
            var r = s.getRange();
            long start = r.lowerEndpoint();
            long duration = r.upperEndpoint() - start;
            Track track = s.getTrack();
            project.undoManager.execute(new UndoManager.RemoveSegCommand(track, s, start, duration, s.getGroup()));
            dirty = true;
        }

        void split(SegActor segActor, long time) {
            splitSegment(segActor.getSegment(), time);
            dirty = true;
        }

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
            var segs = group != null ? List.copyOf(group.getSegments()) : List.of(seg);
            var commands = new ArrayList<UndoManager.UndoableCommand>();
            List<Segment> beforeSegs = new ArrayList<>();
            List<Segment> afterSegs = new ArrayList<>();
            for (Segment member : segs) {
                var r = member.getRange();
                long start = r.lowerEndpoint();
                long end = r.upperEndpoint();
                if (time > start && time < end) {
                    long duration = end - start;
                    var ns = member.duplicate();
                    commands.add(new UndoManager.SplitSegCommand(member.getTrack(), member, start, duration, ns, time));
                    beforeSegs.add(member);
                    afterSegs.add(ns);
                } else if (end <= time) {
                    beforeSegs.add(member);
                } else {
                    afterSegs.add(member);
                }
            }
            if (!commands.isEmpty()) {
                project.undoManager.execute(new UndoManager.CompoundCommand(commands.toArray(new UndoManager.UndoableCommand[0])));
                if (group != null) {
                    for (Segment member : segs) {
                        group.remove(member);
                    }
                    if (beforeSegs.size() >= 2) {
                        SegmentGroup beforeGroup = new SegmentGroup();
                        for (Segment s : beforeSegs) {
                            beforeGroup.add(s);
                        }
                    }
                    if (afterSegs.size() >= 2) {
                        SegmentGroup afterGroup = new SegmentGroup();
                        for (Segment s : afterSegs) {
                            afterGroup.add(s);
                        }
                    }
                }
            }
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
            var r = seg.getRange();
            long start = r.lowerEndpoint();
            long duration = r.upperEndpoint() - start;
            project.undoManager.execute(new UndoManager.RemoveSegCommand(track, seg, start, duration, seg.getGroup()));
            dirty = true;
        }

        void deleteSelected() {
            if (selectedSegments.size() == 0) {
                deleteAtCursor();
                return;
            }

            var cmds = new ArrayList<UndoManager.UndoableCommand>();
            var segs = List.copyOf(selectedSegments.getSegments());

            clearSelection();

            for (Segment seg : segs) {
                var r = seg.getRange();
                long start = r.lowerEndpoint();
                long duration = r.upperEndpoint() - start;
                Track track = seg.getTrack();
                cmds.add(new UndoManager.RemoveSegCommand(track, seg, start, duration, seg.getGroup()));
            }

            project.undoManager.execute(new UndoManager.CompoundCommand(
                cmds.toArray(new UndoManager.UndoableCommand[0])));
            dirty = true;
        }


    }

    // -------------------------------------------------------------------------
    // 内部类：时间线渲染器
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // 内部类：视图状态
    // -------------------------------------------------------------------------

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
