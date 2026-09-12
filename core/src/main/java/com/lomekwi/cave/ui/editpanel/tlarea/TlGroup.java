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
    public final SegMenu segMenu = new SegMenu(this);
    public final TlGroupMenu tlGroupMenu = new TlGroupMenu(this);

    final Timeline timeline;
    final Playhead playhead;
    final Project project;

    final ViewState view = new ViewState();

    /** 拖拽吸附时的吸附时间点，-1 表示无吸附（由 SegActor 拖拽时设置） */
    long snapIndicatorTime = -1;

    boolean dirty = true;
    final SegmentSet selectedSegments = new SegmentSet();

    private static final float KEY_HORIZONTAL_SPEED = 1200f;
    private static final float KEY_VERTICAL_SPEED = 1200f;

    private final Vector2 pointer = new Vector2();

    boolean marqueeActive;
    float marqueeStartX, marqueeStartY;
    float marqueeEndX, marqueeEndY;

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

        // dirty 时按模型 RangeMap 重建 UI；所有 Actor（含拖拽中）都是模型的纯投影：
        // 拖拽只改模型并置 dirty，不做手动摆位，拖拽目标只依赖鼠标轨迹与按下锚点。
        if (dirty) {
            clearChildren(false);

            var visibleRange = view.visibleRange();
            for (int i = timeline.getTracks().size() - 1; i >= 0; i--) {
                final Track track = timeline.getTracks().get(i);

                for (var entry : List.copyOf(track.getSubRangeMapAsEntrySet(visibleRange))) {
                    SegActor actor = entry.getValue().getActor();
                    var r = actor.getSegment().getRange();
                    actor.setPosition(
                        absoluteTimeToX(r.lowerEndpoint()),
                        getHeight() + view.trackYShift - (i + 1) * view.trackHeight
                    );
                    actor.setSize(
                        absoluteTimeToX(r.upperEndpoint()) - absoluteTimeToX(r.lowerEndpoint()),
                        view.trackHeight
                    );
                    addActor(actor);
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
            final float x = absoluteTimeToX(snapIndicatorTime);
            renderer.shapeDrawer.line(x, 0, x, getHeight(), Colors.SNAP_GUIDE, 2);
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

    void removeSeg(SegActor segActor) {
        removeActor(segActor);
        Segment segment = segActor.getSegment();
        try (var h = timeline.record()) {
            timeline.remove(segment);
        }
        dirty = true;
    }

    /** 右键菜单“分割”入口。 */
    void split(SegActor segActor, long time) {
        splitSegment(segActor.getSegment(), time);
        dirty = true;
    }

    /** 快捷键分割入口：按当前鼠标位置定位分割点。 */
    void splitAtCursor() {
        Stage stage = getStage();
        if (stage == null) return;
        Vector2 local = stageToLocalCoordinates(
            stage.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY())));
        int trackIndex = yToTrackIndex(local.y);
        long time = xToAbsoluteTime(local.x);
        Track track = timeline.getTrack(trackIndex);
        Segment segment = track.get(time);
        if (segment == null) return;
        splitSegment(segment, time);
        dirty = true;
    }

    private void splitSegment(Segment segment, long time) {
        SegmentGroup group = segment.getGroup();
        var segments = group != null ? List.copyOf(group) : List.of(segment);
        List<Segment> beforeSegments = new ArrayList<>();
        List<Segment> afterSegments = new ArrayList<>();
        boolean splitAny = false;
        try (var h = timeline.record()) {
            for (Segment member : segments) {
                var range = member.getRange();
                long start = range.lowerEndpoint();
                long end = range.upperEndpoint();
                if (time > start && time < end) {
                    Track track = member.getTrack();
                    timeline.split(track, time);
                    beforeSegments.add(member);
                    afterSegments.add(track.get(time));
                    splitAny = true;
                } else if (end <= time) {
                    beforeSegments.add(member);
                } else {
                    afterSegments.add(member);
                }
            }
        }
        if (splitAny && group != null) {
            for (Segment member : segments) group.remove(member);
            if (beforeSegments.size() >= 2) regroup(beforeSegments);
            if (afterSegments.size() >= 2) regroup(afterSegments);
        }
    }

    private static SegmentGroup regroup(Collection<Segment> members) {
        var group = new SegmentGroup();
        group.addAll(members);
        return group;
    }

    private void deleteAtCursor() {
        Stage stage = getStage();
        if (stage == null) return;
        Vector2 local = stageToLocalCoordinates(
            stage.screenToStageCoordinates(pointer.set(Gdx.input.getX(), Gdx.input.getY())));
        int trackIndex = yToTrackIndex(local.y);
        Track track = timeline.getTrack(trackIndex);
        Segment segment = track.get(xToAbsoluteTime(local.x));
        if (segment == null) return;
        try (var h = timeline.record()) {
            timeline.remove(segment);
        }
        dirty = true;
    }

    void deleteSelected() {
        if (selectedSegments.isEmpty()) {
            deleteAtCursor();
            return;
        }
        var segments = List.copyOf(selectedSegments);
        clearSelection();
        try (var h = timeline.record()) {
            timeline.remove(segments);
        }
        dirty = true;
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

    int yToTrackIndex(float y) {
        final float top = getHeight() + view.trackYShift;
        final float distance = top - y;
        return (int) Math.max(0f,Math.floor(distance / view.trackHeight));
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

    class TimelineRenderer {
        final ShapeDrawer shapeDrawer = App.root.getShapeDrawer();
        private static final float PIXELS_PER_TICK = 200f;

        void drawBackground() {
            shapeDrawer.filledRectangle(0, 0, getWidth(), getHeight(), Colors.TIMELINE_BG);

            final float startX = absoluteTimeToX(0);
            final float endX = absoluteTimeToX(timeline.getLength());

            shapeDrawer.filledRectangle(startX, 0, endX - startX, getHeight(), Colors.TIMELINE_OVERLAY);
        }

        void drawTicks() {
            final long interval = niceScale((long) (view.durationTime * PIXELS_PER_TICK / getWidth()));
            final long start = (view.startTime / interval) * interval;

            for (long t = start; t < view.startTime + view.durationTime; t += interval) {
                float x = absoluteTimeToX(t);
                shapeDrawer.filledRectangle(x, 0, 1, getHeight(), Colors.TIMELINE_OVERLAY);
            }
        }

        void drawTrackBands() {
            final float top = getHeight() + view.trackYShift;
            for (int i = 0; ; i += 2) {
                final float y = top - i * view.trackHeight;
                if (y <= -view.trackHeight) break;
                shapeDrawer.filledRectangle(0, y - view.trackHeight, getWidth(), view.trackHeight, Colors.TIMELINE_OVERLAY);
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
