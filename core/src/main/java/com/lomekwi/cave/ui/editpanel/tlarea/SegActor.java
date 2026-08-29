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
import com.lomekwi.cave.timeline.Track;

import com.lomekwi.cave.app.App;
import com.lomekwi.cave.ui.Colors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 时间线上单个片段的可视化表示与交互入口。 */
public abstract class SegActor extends Actor {
    private final Segment segment;
    TlGroup tl;
    DragSide dragSide=DragSide.NONE;

    // DRAG STATE //////////////////////////
    float firstX = Float.NaN, firstY = Float.NaN;
    private long dragOldStart;
    private long dragOldDuration;
    private List<Segment> dragMembers;
    private long[] dragOrigStarts;
    private long[] dragOrigDurations;
    private Track[] dragOrigTracks;

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
                    for (Segment s : group) {
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
                    for (Segment s : group) {
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
                    boolean alreadySelected = tlGroup.selectedSegments().contains(segment);
                    if (!alreadySelected) {
                        tlGroup.selectSegment(segment, false);
                    }
                    tl = tlGroup;
                    initDrag(x, y);
                    return true;
                } else {
                    getMenu().setContext(SegActor.this,((TlGroup)getParent()).xToAbsoluteTime(getX()+x));
                    return false;
                }
            }
            @Override
            public void touchDragged (InputEvent event, float x, float y, int pointer) {
                dragTo(x, y);
            }
            @Override
            public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
                finishDrag();
                dragSide=DragSide.NONE;
                setCursor(Cursor.SystemCursor.Arrow);
            }
        });
    }
    @Override
    public void draw(Batch batch, float parentAlpha) {
        ScissorStack.calculateScissors(App.root.getStage().getCamera(), batch.getTransformMatrix(),bounds , scissors);
        if (ScissorStack.pushScissors(scissors)) {
            float visibleStartX = 0;
            float visibleEndX = getWidth();
            if (getParent() != null) {
                float parentW = getParent().getWidth();
                visibleStartX = Math.max(0, -getX());
                visibleEndX   = Math.min(getWidth(), parentW - getX());
            }
            drawContent(batch, parentAlpha, visibleStartX, visibleEndX);
            drawBorder();
            drawSelectionOverlay();
            batch.flush();
            ScissorStack.popScissors();
        }
    }
    protected void drawContent(Batch batch, float parentAlpha, float visibleStartX, float visibleEndX){
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

    // 拖拽会话：init → drag → finish 由本 actor 驱动。碰撞由模型把 delta
    // 同向截断到最大可用偏移量处理，无需修正重试。actor 位置是模型的纯投影。

    /** 按下时调用：快照参与拖拽的成员并开始录制 undo。 */
    void initDrag(float diffToActorX, float diffToActorY) {
        var r = segment.getRange();
        dragOldStart = r.lowerEndpoint();
        dragOldDuration = r.upperEndpoint() - dragOldStart;
        firstX = diffToActorX;
        firstY = diffToActorY;
        tl.timeline.record();
        initDragMembers();
    }

    /** 拖拽中：每次鼠标移动都会调用，按 dragSide 分派到三种分支（含吸附）。 */
    void dragTo(float diffToActorX, float diffToActorY) {
        if (tl == null || dragSide == DragSide.NONE) return;

        tl.snapIndicatorTime = -1;

        switch (dragSide) {
            case FRONT: {
                // target = 鼠标 stage x（diffToActorX 与 getX() 相消）
                float target = getX() + diffToActorX;
                target = Math.max(target, tl.absoluteTimeToX(0));
                handleFrontResize(snapResizeTime(Math.max(tl.xToAbsoluteTime(target), 0)));
                break;
            }
            case BEHIND: {
                float upper = getX() + diffToActorX;
                long rawUpper = Math.max(tl.xToAbsoluteTime(upper), 0);
                handleBehindResize(snapResizeTime(rawUpper));
                break;
            }
            case MIDDLE: {
                // deltaX/deltaY 为相对按下点的累计位移，同帧多次 mouse move 不会重复累加
                float deltaX = diffToActorX - firstX;
                float deltaY = diffToActorY - firstY;
                float targetX = getX() + deltaX;
                float targetY = getY() + deltaY;

                long duration = segment.getRange().upperEndpoint() - segment.getRange().lowerEndpoint();
                long target = tl.xToAbsoluteTime(targetX);
                if (target < 0) target = 0;
                target = snapMoveTarget(target, duration);

                var newTrack = tl.timeline.getTrack(Math.max(0, tl.yToTrackIndex(targetY + tl.view.trackHeight / 2)));

                handleMiddleDrag(target, newTrack);
                break;
            }
        }

        tl.dirty = true;
    }

    // 吸附点由 Timeline.snapTime 获取，这里只做阈值换算、忽略集与指示线。

    private static final float SNAP_THRESHOLD_PX = 10f;

    private long snapThreshold() {
        return Math.max(1, (long) (SNAP_THRESHOLD_PX / tl.getWidth() * tl.view.durationTime));
    }

    private boolean snapDisabled() {
        return App.shortcutManager.isActive(TlGroup.Actions.SNAP_IGNORE);
    }

    /** 裁切吸附：忽略 drag 成员与同轨道片段，返回吸附后的时间并设置指示线。 */
    private long snapResizeTime(long rawTime) {
        if (snapDisabled()) return rawTime;
        Set<Segment> ignore = new HashSet<>(dragMembers);
        for (Segment s : segment.getTrack()) ignore.add(s);
        long snapped = tl.timeline.snapTime(rawTime, snapThreshold(), ignore);
        if (snapped != rawTime) tl.snapIndicatorTime = snapped;
        return snapped;
    }

    /** 整体移动吸附：起点与终点各求吸附点，取更近者。 */
    private long snapMoveTarget(long target, long duration) {
        if (snapDisabled()) return target;
        Set<Segment> ignore = new HashSet<>(dragMembers);
        long segEnd = target + duration;
        long snappedStart = tl.timeline.snapTime(target, snapThreshold(), ignore);
        long snappedEnd = tl.timeline.snapTime(segEnd, snapThreshold(), ignore) - duration;
        if (snappedEnd < 0) snappedEnd = 0;
        boolean startMoved = snappedStart != target;
        boolean endMoved = snappedEnd != target;
        if (startMoved && endMoved) {
            if (Math.abs(snappedStart - target) <= Math.abs(snappedEnd - target)) {
                tl.snapIndicatorTime = snappedStart;
                return snappedStart;
            }
            tl.snapIndicatorTime = snappedEnd + duration;
            return snappedEnd;
        } else if (startMoved) {
            tl.snapIndicatorTime = snappedStart;
            return snappedStart;
        } else if (endMoved) {
            tl.snapIndicatorTime = snappedEnd + duration;
            return snappedEnd;
        }
        return target;
    }

    /** 松手时调用：提交 undo 并清空会话。 */
    void finishDrag() {
        if (tl == null) return;
        tl.dirty = true;
        tl.snapIndicatorTime = -1;
        tl.timeline.submit();
        dragMembers = null;
        dragOrigStarts = null;
        dragOrigDurations = null;
        dragOrigTracks = null;
    }

    /** 收集参与拖拽的成员并快照各自的起点/时长/轨道。 */
    private void initDragMembers() {
        var selected = tl.selectedSegments;
        if (selected.size() > 1 && selected.contains(segment)) {
            dragMembers = new ArrayList<>(selected.size());
            dragMembers.add(segment);
            for (Segment s : selected) {
                if (s != segment) dragMembers.add(s);
            }
        } else {
            dragMembers = new ArrayList<>(1);
            dragMembers.add(segment);
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

    // 整体平移。模型 moveTime/moveTrack 都按相对当前位置位移，因此这里的
    // delta 必须相对当前模型状态，避免同帧多次 mouse move 反复累加。
    // 模型内部会把 delta 同向截断到最大可用偏移量（撞上障碍即贴合）后应用，
    // 因此每次 mouse move 一次调用即可，无需按修正量重试。
    private void handleMiddleDrag(long target, Track newTrack) {
        List<Segment> members = List.copyOf(dragMembers);

        int trackDelta = newTrack.index - members.get(0).getTrack().index;

        int minIdx = members.stream().mapToInt(m -> m.getTrack().index).min().orElseThrow();
        if (minIdx + trackDelta < 0) return;

        long currentStart0 = members.get(0).getRange().lowerEndpoint();
        tl.timeline.moveTime(members, target - currentStart0);

        if (trackDelta != 0) {
            tl.timeline.moveTrack(members, trackDelta);
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
        long currentStart0 = members.get(0).getRange().lowerEndpoint();

        // 模型内部把 delta 同向截断到最大可用偏移量（自身长度/前邻/起点下界）后应用；
        // applied 为 0 等价于没动。
        tl.timeline.setStart(members, newStart - currentStart0);
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
        long currentEnd0 = members.get(0).getRange().upperEndpoint();

        // 模型内部把 delta 同向截断到最大可用偏移量（自身长度/后邻/源长度上界）后应用；
        // applied 为 0 等价于没动。
        tl.timeline.setEnd(members, newEnd - currentEnd0);
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
