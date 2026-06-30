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
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.google.common.collect.Range;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.app.shortcut.ShortcutAction;
import com.lomekwi.cave.resource.media.MediaFactory;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.SegmentGroup;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.project.ProjectFrontedEvent;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.util.MimeType;

import com.lomekwi.cave.app.App;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import space.earlygrey.shapedrawer.ShapeDrawer;

import static com.badlogic.gdx.Input.Keys.*;

import org.jspecify.annotations.NonNull;

public class TlGroup extends Group {

    private final TimelineRenderer renderer = new TimelineRenderer();
    final SegDragHandler dragHandler = new SegDragHandler();

    private final Timeline timeline;
    private final Playhead playhead;
    private final Project project;

    final ViewState view = new ViewState();

    private boolean dirty = true;
    private final Set<Segment> selectedSegments = new HashSet<>();

    private static final float KEY_HORIZONTAL_SPEED = 1200f;
    private static final float KEY_VERTICAL_SPEED = 1200f;

    private final Vector2 pointer = new Vector2();

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
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button == Input.Buttons.LEFT) {
                    Gdx.app.log("TlGroup", "regular touchDown x=" + x + " y=" + y);
                    clearSelection();
                    playhead.seek(Math.max(xToAbsoluteTime(x), 0));
                    return true;
                }
                return false;
            }
            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                playhead.seek(Math.max(xToAbsoluteTime(x), 0));
            }
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
                if (App.shortcutManager.isActive(Actions.PLAY_PAUSE)) {
                    playhead.setPlaying(!playhead.isPlaying());
                }
                if (App.shortcutManager.isActive(Actions.SPLIT)) {
                    dragHandler.splitAtCursor();
                }
                if (App.shortcutManager.isActive(Actions.DELETE)) {
                    dragHandler.deleteAtCursor();
                }
                if (App.shortcutManager.isActive(Actions.UNDO)) {
                    project.undoManager.undo();
                    dirty = true;
                }
                if (App.shortcutManager.isActive(Actions.REDO)) {
                    project.undoManager.redo();
                    dirty = true;
                }
                if (App.shortcutManager.isActive(Actions.SELECT_ADD)) {
                    dragHandler.selectAtCursor();
                    return true;
                }
                if (App.shortcutManager.isActive(Actions.GROUP)) {
                    groupSelectedSegments();
                    return true;
                }
                return true;
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                App.root.getStage().setScrollFocus(TlGroup.this);
                App.root.getStage().setKeyboardFocus(TlGroup.this);
            }
        });

        addCaptureListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;
                playhead.seek(Math.max(xToAbsoluteTime(x), 0));
                return false;
            }
        });

        App.root.getDragAndDrop().addTarget(new DragAndDrop.Target(this) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                if (!(payload.getObject() instanceof File file)) {
                    return false;
                }
                String mimeType = MimeType.detectMimeType(file);
                return mimeType != null && MediaFactory.isSupported(mimeType);
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                try {
                    File file = (File) payload.getObject();
                    List<Segment> segments = project.segFactory.getAll(file);
                    long startTime = xToAbsoluteTime(x);
                    int baseTrack = yToTrackIndex(y);
                    int trackOffset = 0;
                    var cmds = new ArrayList<UndoManager.UndoableCommand>();
                    List<Segment> added = new ArrayList<>();
                    for (Segment seg : segments) {
                        seg.setOrigin(startTime);
                        long duration = seg.getDuration();
                        if (duration <= 0) continue;
                        int targetTrack = baseTrack + trackOffset;
                        var range = Range.closedOpen(startTime, startTime + duration);
                        while (!timeline.getTrack(targetTrack).isFree(range)) {
                            targetTrack++;
                        }
                        timeline.add(timeline.getTrack(targetTrack), seg, startTime, duration);
                        cmds.add(new UndoManager.AddSegCommand(timeline.getTrack(targetTrack), seg, startTime, duration));
                        trackOffset = targetTrack - baseTrack + 1;
                        added.add(seg);
                    }
                    if (!cmds.isEmpty()) {
                        project.undoManager.push(new UndoManager.CompoundCommand(cmds.toArray(new UndoManager.UndoableCommand[0])));
                    }
                    if (added.size() >= 2) {
                        SegmentGroup group = new SegmentGroup();
                        for (Segment seg : added) {
                            group.add(seg);
                        }
                    }
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

        if (getStage() != null && getStage().getKeyboardFocus() == this) {
            final float timePerPixel = (float) view.durationTime / getWidth();
            boolean acted = false;

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

            if (acted) dirty = true;
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
                }
            }

            dirty = false;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        renderer.drawBackground();
        renderer.drawSplitters();
        super.draw(batch, parentAlpha);
        renderer.drawPlayhead();
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

    void selectSegment(Segment segment, boolean addToSelection) {
        if (!addToSelection) {
            clearSelection();
        }
        if (selectedSegments.contains(segment)) {
            selectedSegments.remove(segment);
            segment.setSelected(false);
        } else {
            selectedSegments.add(segment);
            segment.setSelected(true);
        }
    }

    void clearSelection() {
        for (Segment seg : selectedSegments) {
            seg.setSelected(false);
        }
        selectedSegments.clear();
    }

    private void groupSelectedSegments() {
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
                dissolvedMembers.put(g, new HashSet<>(g.getSegments()));
            }

            for (Segment seg : selectedSegments) {
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

            project.undoManager.push(new UndoManager.UndoableCommand() {
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
            List<Segment> segs = new ArrayList<>(selectedSegments);

            for (Segment seg : segs) {
                group.add(seg);
            }

            project.undoManager.push(new UndoManager.UndoableCommand() {
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

    private void removeFromGroup(Segment segment) {
        SegmentGroup g = segment.getGroup();
        if (g != null) {
            g.remove(segment);
            if (g.size() < 2) {
                for (Segment s : new HashSet<>(g.getSegments())) {
                    g.remove(s);
                }
            }
        }
    }

    @Subscribe
    public void onProjectFronted(ProjectFrontedEvent e) {
        App.root.getStage().setScrollFocus(this);
        App.root.getStage().setKeyboardFocus(this);
    }

    // -- delegating to SegDragHandler --

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

    private int yToTrackIndex(float y) {
        final float top = getHeight() + view.trackYShift;
        final float distance = top - y;
        return (int) Math.floor(distance / view.trackHeight);
    }

    private float trackIndexToTopY(int index) {
        return getHeight() + view.trackYShift - index * view.trackHeight;
    }

    @SuppressWarnings("unused")
    private float trackIndexToBottomY(int index) {
        return trackIndexToTopY(index) - view.trackHeight;
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
        private boolean dragActive;

        // Group drag state
        private List<Segment> groupMembers;
        private long[] groupOrigStarts;
        private long[] groupOrigDurations;
        private Track[] groupOrigTracks;

        void segDrag(SegActor actor, float diffToActorX, float diffToActorY) {
            if (!dragActive) {
                var seg = actor.getSegment();
                var r = seg.getRange();
                dragOldStart = r.lowerEndpoint();
                dragOldDuration = r.upperEndpoint() - dragOldStart;
                dragOldTrack = seg.getTrack();
                dragActive = true;
                initGroupDrag(seg);
            }

            Track t = actor.getSegment().getTrack();
            var e = actor.getSegment().getEntry();
            var r = e.getKey();

            switch (actor.getDragSide()) {
                case FRONT: {
                    float upper = actor.getX() + actor.getWidth();
                    float target = actor.getX() + diffToActorX;
                    if (target >= upper) return;
                    target = Math.max(target, absoluteTimeToX(0));

                    if (groupMembers != null) {
                        handleGroupFrontResize(actor, xToAbsoluteTime(target));
                        break;
                    }

                    Range<Long> nr = Range.closedOpen(xToAbsoluteTime(target), r.upperEndpoint());

                    if (!t.isFree(e, nr)) {
                        long minStart = t.getEntry(r.lowerEndpoint() + 1, -1, true).getKey().upperEndpoint();
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

                    if (groupMembers != null) {
                        handleGroupBehindResize(actor, xToAbsoluteTime(upper));
                        break;
                    }

                    Range<Long> nr = Range.closedOpen(r.lowerEndpoint(), xToAbsoluteTime(upper));

                    if (!t.isFree(e, nr)) {
                        long maxEnd = t.getEntry(r.upperEndpoint() - 1, 1, true).getKey().lowerEndpoint();
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
                        actor.setDragInvalid(false);
                        return;
                    }

                    float deltaX = diffToActorX - firstX;
                    float deltaY = diffToActorY - firstY;
                    float targetX = Math.max(actor.getX() + deltaX, 0f);
                    float targetY = actor.getY() + deltaY;

                    long duration = r.upperEndpoint() - r.lowerEndpoint();
                    long target = xToAbsoluteTime(targetX);

                    var newTrack = timeline.getTrack(Math.max(0, yToTrackIndex(targetY + view.trackHeight / 2)));

                    if (groupMembers != null) {
                        handleGroupMiddleDrag(actor, target, newTrack);
                        break;
                    }

                    var nr = Range.closedOpen(target, target + duration);
                    boolean canMove = newTrack.isFree(e, nr);

                    if (!canMove) {
                        long snappedTarget = -1;
                        long maxOccEnd = -1;
                        long minOccStart = Long.MAX_VALUE;
                        for (var occ : newTrack.getSubRangeMapAsEntrySet(Range.closedOpen(target, target + duration))) {
                            if (occ.getValue() == e.getValue()) continue;
                            maxOccEnd = Math.max(maxOccEnd, occ.getKey().upperEndpoint());
                            minOccStart = Math.min(minOccStart, occ.getKey().lowerEndpoint());
                        }

                        long rightTarget = maxOccEnd >= 0 ? maxOccEnd : -1;
                        long leftTarget = minOccStart < Long.MAX_VALUE && minOccStart >= duration
                            ? minOccStart - duration : -1;

                        if (rightTarget >= 0 && newTrack.isFree(e, Range.closedOpen(rightTarget, rightTarget + duration))) {
                            snappedTarget = rightTarget;
                        }
                        if (leftTarget >= 0 && newTrack.isFree(e, Range.closedOpen(leftTarget, leftTarget + duration))) {
                            if (snappedTarget < 0 || Math.abs(leftTarget - target) < Math.abs(snappedTarget - target)) {
                                snappedTarget = leftTarget;
                            }
                        }

                        if (snappedTarget >= 0) {
                            target = snappedTarget;
                            targetX = absoluteTimeToX(target);
                            /*nr = Range.closedOpen(target, target + duration);*/
                            canMove = true;
                        }
                    }

                    actor.setDragInvalid(!canMove);
                    actor.setPosition(targetX, targetY);

                    if (canMove) {
                        long segmentStart = r.lowerEndpoint();
                        timeline.move(t, newTrack, e, target, duration);
                        e.getValue().offsetOrigin(target - segmentStart);
                    }
                }
            }
        }

        private void initGroupDrag(Segment seg) {
            SegmentGroup group = seg.getGroup();
            if (group == null) return;

            List<Segment> others = new ArrayList<>();
            for (Segment s : group.getSegments()) {
                if (s != seg) others.add(s);
            }
            if (others.isEmpty()) return;

            groupMembers = new ArrayList<>();
            groupMembers.add(seg);
            groupMembers.addAll(others);

            int n = groupMembers.size();
            groupOrigStarts = new long[n];
            groupOrigDurations = new long[n];
            groupOrigTracks = new Track[n];
            for (int i = 0; i < n; i++) {
                var sr = groupMembers.get(i).getRange();
                groupOrigStarts[i] = sr.lowerEndpoint();
                groupOrigDurations[i] = sr.upperEndpoint() - sr.lowerEndpoint();
                groupOrigTracks[i] = groupMembers.get(i).getTrack();
            }
        }

        private void handleGroupMiddleDrag(SegActor actor, long target, Track newTrack) {

            long timeDelta = target - dragOldStart;
            int trackDelta = newTrack.index - dragOldTrack.index;

            int n = groupMembers.size();

            // Save current state for revert
            long[] prevStarts = new long[n];
            long[] prevDurations = new long[n];
            Track[] prevTracks = new Track[n];
            long[] prevOrigins = new long[n];
            for (int i = 0; i < n; i++) {
                var r = groupMembers.get(i).getRange();
                prevStarts[i] = r.lowerEndpoint();
                prevDurations[i] = r.upperEndpoint() - r.lowerEndpoint();
                prevTracks[i] = groupMembers.get(i).getTrack();
                prevOrigins[i] = groupMembers.get(i).getOrigin();
            }

            // Temporarily remove all group members from timeline
            for (int i = 0; i < n; i++) {
                timeline.remove(prevTracks[i], Range.closedOpen(prevStarts[i], prevStarts[i] + prevDurations[i]));
            }

            // Pre-check all target positions
            boolean allValid = true;
            long[] newStarts = new long[n];
            Track[] newTracks = new Track[n];

            for (int i = 0; i < n && allValid; i++) {
                long msTarget = groupOrigStarts[i] + timeDelta;
                int targetTrackIdx = groupOrigTracks[i].index + trackDelta;
                if (targetTrackIdx < 0) {
                    allValid = false;
                    break;
                }
                newStarts[i] = msTarget;
                newTracks[i] = timeline.getTrack(targetTrackIdx);
                var msRange = Range.closedOpen(msTarget, msTarget + groupOrigDurations[i]);
                if (!newTracks[i].isFree(msRange)) {
                    allValid = false;
                }
            }

            if (!allValid) {
                for (int i = 0; i < n; i++) {
                    timeline.add(prevTracks[i], groupMembers.get(i), prevStarts[i], prevDurations[i]);
                    groupMembers.get(i).setOrigin(prevOrigins[i]);
                }
                actor.setDragInvalid(true);
                return;
            }

            // All valid - add all at new positions
            for (int i = 0; i < n; i++) {
                Segment ms = groupMembers.get(i);
                timeline.add(newTracks[i], ms, newStarts[i], groupOrigDurations[i]);
                ms.offsetOrigin(newStarts[i] - prevStarts[i]);

                SegActor msActor = ms.getActor();
                msActor.setPosition(
                    absoluteTimeToX(newStarts[i]),
                    getHeight() + view.trackYShift - (newTracks[i].index + 1) * view.trackHeight
                );
                msActor.setSize(
                    absoluteTimeToX(newStarts[i] + groupOrigDurations[i]) - absoluteTimeToX(newStarts[i]),
                    view.trackHeight
                );
            }

            actor.setDragInvalid(false);
        }

        private void handleGroupFrontResize(SegActor actor, long newStart) {
            long timeDelta = newStart - dragOldStart;
            int n = groupMembers.size();

            long[] prevStarts = new long[n];
            long[] prevDurations = new long[n];
            Track[] prevTracks = new Track[n];
            long[] prevOrigins = new long[n];
            for (int i = 0; i < n; i++) {
                var r = groupMembers.get(i).getRange();
                prevStarts[i] = r.lowerEndpoint();
                prevDurations[i] = r.upperEndpoint() - r.lowerEndpoint();
                prevTracks[i] = groupMembers.get(i).getTrack();
                prevOrigins[i] = groupMembers.get(i).getOrigin();
            }

            for (int i = 0; i < n; i++) {
                timeline.remove(prevTracks[i], Range.closedOpen(prevStarts[i], prevStarts[i] + prevDurations[i]));
            }

            boolean allValid = true;
            long[] newStarts = new long[n];
            for (int i = 0; i < n && allValid; i++) {
                long msNewStart = groupOrigStarts[i] + timeDelta;
                long msOldEnd = groupOrigStarts[i] + groupOrigDurations[i];
                if (msNewStart >= msOldEnd || msNewStart < 0) {
                    allValid = false;
                    break;
                }
                newStarts[i] = msNewStart;
                var msRange = Range.closedOpen(msNewStart, msOldEnd);
                if (!groupOrigTracks[i].isFree(msRange)) {
                    allValid = false;
                }
            }

            if (!allValid) {
                for (int i = 0; i < n; i++) {
                    timeline.add(prevTracks[i], groupMembers.get(i), prevStarts[i], prevDurations[i]);
                    groupMembers.get(i).setOrigin(prevOrigins[i]);
                }
                actor.setDragInvalid(true);
                return;
            }

            for (int i = 0; i < n; i++) {
                Segment ms = groupMembers.get(i);
                long msNewStart = newStarts[i];
                long msOldEnd = groupOrigStarts[i] + groupOrigDurations[i];
                timeline.add(groupOrigTracks[i], ms, msNewStart, msOldEnd - msNewStart);

                SegActor msActor = ms.getActor();
                msActor.setX(absoluteTimeToX(msNewStart));
                msActor.setWidth(absoluteTimeToX(msOldEnd) - absoluteTimeToX(msNewStart));
            }

            actor.setDragInvalid(false);
        }

        private void handleGroupBehindResize(SegActor actor, long newEnd) {
            long oldEnd = dragOldStart + dragOldDuration;
            long timeDelta = newEnd - oldEnd;
            int n = groupMembers.size();

            long[] prevStarts = new long[n];
            long[] prevDurations = new long[n];
            Track[] prevTracks = new Track[n];
            long[] prevOrigins = new long[n];
            for (int i = 0; i < n; i++) {
                var r = groupMembers.get(i).getRange();
                prevStarts[i] = r.lowerEndpoint();
                prevDurations[i] = r.upperEndpoint() - r.lowerEndpoint();
                prevTracks[i] = groupMembers.get(i).getTrack();
                prevOrigins[i] = groupMembers.get(i).getOrigin();
            }

            for (int i = 0; i < n; i++) {
                timeline.remove(prevTracks[i], Range.closedOpen(prevStarts[i], prevStarts[i] + prevDurations[i]));
            }

            boolean allValid = true;
            long[] newDurations = new long[n];
            for (int i = 0; i < n && allValid; i++) {
                long msOldEnd = groupOrigStarts[i] + groupOrigDurations[i];
                long msNewEnd = msOldEnd + timeDelta;
                if (msNewEnd <= groupOrigStarts[i]) {
                    allValid = false;
                    break;
                }
                newDurations[i] = msNewEnd - groupOrigStarts[i];
                var msRange = Range.closedOpen(groupOrigStarts[i], msNewEnd);
                if (!groupOrigTracks[i].isFree(msRange)) {
                    allValid = false;
                }
            }

            if (!allValid) {
                for (int i = 0; i < n; i++) {
                    timeline.add(prevTracks[i], groupMembers.get(i), prevStarts[i], prevDurations[i]);
                    groupMembers.get(i).setOrigin(prevOrigins[i]);
                }
                actor.setDragInvalid(true);
                return;
            }

            for (int i = 0; i < n; i++) {
                Segment ms = groupMembers.get(i);
                timeline.add(groupOrigTracks[i], ms, groupOrigStarts[i], newDurations[i]);

                SegActor msActor = ms.getActor();
                msActor.setWidth(
                    absoluteTimeToX(groupOrigStarts[i] + newDurations[i]) - absoluteTimeToX(groupOrigStarts[i]));
            }

            actor.setDragInvalid(false);
        }

        void segDragEnd(SegActor actor) {
            dirty = true;
            firstX = Float.NaN;

            if (dragActive) {
                var seg = actor.getSegment();

                if (groupMembers != null) {
                    var cmds = getUndoableCommands(actor);
                    if (!cmds.isEmpty()) {
                        project.undoManager.push(
                            new UndoManager.CompoundCommand(cmds.toArray(new UndoManager.UndoableCommand[0])));
                    }
                } else {
                    var r = seg.getRange();
                    long newStart = r.lowerEndpoint();
                    long newDuration = r.upperEndpoint() - newStart;
                    Track newTrack = seg.getTrack();

                    if (dragOldStart != newStart || dragOldDuration != newDuration || dragOldTrack != newTrack) {
                        if (actor.getDragSide() == DragSide.MIDDLE) {
                            project.undoManager.push(new UndoManager.MoveSegCommand(
                                dragOldTrack, newTrack, seg, dragOldStart, dragOldDuration, newStart, newDuration));
                        } else {
                            project.undoManager.push(new UndoManager.ResizeSegCommand(
                                newTrack, seg, dragOldStart, dragOldDuration, newStart, newDuration));
                        }
                    }
                }

                dragActive = false;
            }
            actor.setDragInvalid(false);

            groupMembers = null;
            groupOrigStarts = null;
            groupOrigDurations = null;
            groupOrigTracks = null;
        }

    private @NonNull ArrayList<UndoManager.UndoableCommand> getUndoableCommands(SegActor actor) {
        int n = groupMembers.size();
        var cmds = new ArrayList<UndoManager.UndoableCommand>();
        if (actor.getDragSide() == DragSide.MIDDLE) {
            for (int i = 0; i < n; i++) {
                Segment ms = groupMembers.get(i);
                var msr = ms.getRange();
                long newStart = msr.lowerEndpoint();
                long newDuration = msr.upperEndpoint() - newStart;
                Track newTrack = ms.getTrack();

                if (groupOrigStarts[i] != newStart
                    || groupOrigDurations[i] != newDuration
                    || groupOrigTracks[i] != newTrack) {
                    cmds.add(new UndoManager.MoveSegCommand(
                        groupOrigTracks[i], newTrack, ms,
                        groupOrigStarts[i], groupOrigDurations[i], newStart, newDuration));
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                Segment ms = groupMembers.get(i);
                var msr = ms.getRange();
                long newStart = msr.lowerEndpoint();
                long newDuration = msr.upperEndpoint() - newStart;

                if (groupOrigStarts[i] != newStart
                    || groupOrigDurations[i] != newDuration) {
                    cmds.add(new UndoManager.ResizeSegCommand(
                        groupOrigTracks[i], ms,
                        groupOrigStarts[i], groupOrigDurations[i], newStart, newDuration));
                }
            }
        }
        return cmds;
    }

    void removeSeg(SegActor segActor) {
            removeActor(segActor);
            Segment s = segActor.getSegment();
            removeFromGroup(s);
            var r = s.getRange();
            long start = r.lowerEndpoint();
            long duration = r.upperEndpoint() - start;
            Track track = s.getTrack();
            project.undoManager.execute(new UndoManager.RemoveSegCommand(track, s, start, duration));
            dirty = true;
        }

        void split(SegActor segActor, long time) {
            var s = segActor.getSegment();
            Track track = s.getTrack();
            var r = s.getRange();
            long start = r.lowerEndpoint();
            long duration = r.upperEndpoint() - start;
            var ns = s.duplicate();
            project.undoManager.execute(new UndoManager.SplitSegCommand(track, s, start, duration, ns, time));
            dirty = true;
        }

        private void splitAtCursor() {
            Stage s = getStage();
            if (s == null) return;
            Vector2 local = stageToLocalCoordinates(
                s.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY())));
            int trackIndex = yToTrackIndex(local.y);
            if (trackIndex < 0 || trackIndex >= timeline.getTracks().size()) return;
            long time = xToAbsoluteTime(local.x);
            Track track = timeline.getTrack(trackIndex);
            var entry = track.getEntry(time);
            if (entry == null) return;
            var seg = entry.getValue();
            var r = entry.getKey();
            long start = r.lowerEndpoint();
            long duration = r.upperEndpoint() - start;
            var ns = seg.duplicate();
            project.undoManager.execute(new UndoManager.SplitSegCommand(track, seg, start, duration, ns, time));
            dirty = true;
        }

        private void deleteAtCursor() {
            Stage s = getStage();
            if (s == null) return;
            Vector2 local = stageToLocalCoordinates(
                s.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY())));
            int trackIndex = yToTrackIndex(local.y);
            if (trackIndex < 0 || trackIndex >= timeline.getTracks().size()) return;
            Track track = timeline.getTrack(trackIndex);
            var entry = track.getEntry(xToAbsoluteTime(local.x));
            if (entry != null) {
                var seg = entry.getValue();
                removeFromGroup(seg);
                var r = seg.getRange();
                long start = r.lowerEndpoint();
                long duration = r.upperEndpoint() - start;
                project.undoManager.execute(new UndoManager.RemoveSegCommand(track, seg, start, duration));
            }
            dirty = true;
        }

        private void selectAtCursor() {
            Stage s = getStage();
            if (s == null) return;
            Vector2 local = stageToLocalCoordinates(
                s.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY())));
            int trackIndex = yToTrackIndex(local.y);
            if (trackIndex >= 0 && trackIndex < timeline.getTracks().size()) {
                long time = xToAbsoluteTime(local.x);
                var entry = timeline.getTrack(trackIndex).getEntry(time);
                if (entry != null) {
                    boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
                    selectSegment(entry.getValue(), ctrl);
                    return;
                }
            }
            clearSelection();
        }
    }

    // -------------------------------------------------------------------------
    // 内部类：时间线渲染器
    // -------------------------------------------------------------------------

    class TimelineRenderer {
        private final ShapeDrawer shapeDrawer = App.root.getShapeDrawer();
        private final Color black = new Color(Color.BLACK).add(0, 0, 0, -0.5f);

        void drawBackground() {
            shapeDrawer.filledRectangle(0, 0, getWidth(), getHeight(), Color.DARK_GRAY);

            final float startX = absoluteTimeToX(0);
            final float endX = absoluteTimeToX(timeline.getLength());

            shapeDrawer.filledRectangle(startX, 0, endX - startX, getHeight(), Color.GRAY);
        }

        void drawSplitters() {
            float offset = ((view.trackYShift % view.trackHeight) + view.trackHeight) % view.trackHeight;
            float startY = getHeight() + offset;

            for (float y = startY; y > -view.trackHeight; y -= view.trackHeight) {
                shapeDrawer.line(0, y, getWidth(), y, black);
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
    }

    public enum Actions implements ShortcutAction {
        SCROLL_LEFT("向左滚动", A),
        SCROLL_RIGHT("向右滚动", D),
        SCROLL_UP("向上滚动", W),
        SCROLL_DOWN("向下滚动", S),
        SPLIT("分割", Q),
        DELETE("删除", X),
        UNDO("撤销", CONTROL_LEFT, Z),
        REDO("重做", CONTROL_LEFT, SHIFT_LEFT, Z),
        PLAY_PAUSE("播放/暂停", SPACE),
        GROUP("分组", F),
        SELECT_ADD("附加选择", E);

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
