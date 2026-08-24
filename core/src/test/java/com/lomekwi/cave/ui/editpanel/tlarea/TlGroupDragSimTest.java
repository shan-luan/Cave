package com.lomekwi.cave.ui.editpanel.tlarea;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Range;
import com.lomekwi.cave.project.TestProject;
import com.lomekwi.cave.timeline.GdxTestBase;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.SegmentSet;
import com.lomekwi.cave.timeline.TestSource;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.Track;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;

/**
 * 用真实 SegDragHandler 模拟"鼠标拖拽"交互，验证重构后的 UI 拖拽逻辑：
 * 1) 小幅拖拽不应被放大成大幅移动（多次 act() 重建喂回必须幂等）；
 * 2) 拖拽边缘裁切时不该改变片段 origin。
 *
 * 说明：TlGroup 的字段初始化会创建 vis-ui 菜单组件（需已加载 Skin），
 * 在 headless 测试里不便构造。因此这里用 Objenesis 绕过构造函数实例化，
 * 再反射填入拖拽处理器真正依赖的模型/视图字段，其余 UI 保持空。
 * actor 不挂 parent（Group 内部 children 未初始化），但处理器只读它的位置/尺寸。
 */
public class TlGroupDragSimTest extends GdxTestBase {

    private static final float WIDTH = 2000f;
    private static final float HEIGHT = 400f;
    private static final float TRACK_H = 80f;
    // durationTime 单位为微秒（µs），这里 1px = 1000µs = 1ms
    private static final long DURATION_US = 2_000_000L;

    private TestProject project;
    private Timeline timeline;
    private TlGroup tl;
    private TlGroup.ViewState view;

    @Before
    public void setUp() throws Exception {
        project = new TestProject();
        timeline = project.timeline;

        tl = new ObjenesisStd().newInstance(TlGroup.class);
        tl.setSize(WIDTH, HEIGHT);

        setField(tl, "timeline", timeline);
        setField(tl, "project", project);
        setField(tl, "selectedSegments", new SegmentSet());
        setField(tl, "dirty", true);
        setField(tl, "snapIndicatorTime", -1L);

        view = new TlGroup.ViewState();
        view.startTime = 0;
        view.durationTime = DURATION_US;
        view.trackHeight = TRACK_H;
        view.trackYShift = 0;
        setField(tl, "view", view);

        TlGroup.SegDragHandler handler = tl.new SegDragHandler();
        setField(tl, "dragHandler", handler);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private Segment newSeg(long duration) {
        return new Segment(new TestSource(duration));
    }

    private float absX(long time) {
        return view.timeToX(time, tl.getWidth());
    }

    private float trackTopY(int index) {
        return tl.getHeight() + view.trackYShift - (index + 1) * view.trackHeight;
    }

    /** 在模型上放置一个片段，并按 act() 的 NONE 逻辑摆好 Actor。 */
    private SegActor place(Track track, Segment s, long start, long end) {
        timeline.tryAdd(track, s, Range.closedOpen(start, end));
        SegActor actor = s.getActor();
        actor.setPosition(absX(start), trackTopY(track.index));
        actor.setSize(absX(end) - absX(start), view.trackHeight);
        return actor;
    }

    private void setDragSide(SegActor actor, DragSide side) throws Exception {
        Field f = SegActor.class.getDeclaredField("dragSide");
        f.setAccessible(true);
        f.set(actor, side);
    }

    /** 模拟 act() 重建：把 actor 贴回模型，再用固定鼠标位置喂给 handler。 */
    private void rebuildAndFeed(SegActor actor, float mouseLocalX, float mouseLocalY) {
        Segment s = actor.getSegment();
        var r = s.getRange();
        actor.setPosition(absX(r.lowerEndpoint()), trackTopY(s.getTrack().index));
        actor.setSize(absX(r.upperEndpoint()) - absX(r.lowerEndpoint()), view.trackHeight);
        tl.segDrag(actor, mouseLocalX - actor.getX(), mouseLocalY - actor.getY());
    }

    // ---------------------------------------------------------------------
    // 中部整体移动：小幅拖拽不应被放大
    // ---------------------------------------------------------------------

    @Test
    public void middleDragMovesBySameDeltaAndIsStableAndUndoable() throws Exception {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(1000_000L); // 时长 1s
        s.setOrigin(5_000_000L);
        SegActor actor = place(t0, s, 0, 1000_000L);

        float firstX = actor.getWidth() / 2;
        float firstY = view.trackHeight / 2;
        setDragSide(actor, DragSide.MIDDLE);
        tl.initDrag(actor, firstX, firstY);

        float mouseLocalX = actor.getX() + firstX + 100f; // 右移 100px
        float mouseLocalY = actor.getY() + firstY;

        // 事件驱动一次：应恰好移动 100_000µs
        tl.segDrag(actor, mouseLocalX - actor.getX(), mouseLocalY - actor.getY());
        assertEquals(Range.closedOpen(100_000L, 1000_000L + 100_000L), s.getRange());

        // 鼠标不动，多帧重建喂回：位置必须稳定（幂等）
        for (int i = 0; i < 5; i++) {
            rebuildAndFeed(actor, mouseLocalX, mouseLocalY);
            assertEquals(Range.closedOpen(100_000L, 1000_000L + 100_000L), s.getRange());
            assertEquals(5_000_000L + 100_000L, s.getOrigin());
        }

        tl.segDragEnd(actor);

        project.undoManager.undo();
        assertEquals(Range.closedOpen(0L, 1000_000L), s.getRange());
        assertEquals(5_000_000L, s.getOrigin());
    }

    // ---------------------------------------------------------------------
    // 竖直方向整个移动：拖动一小段距离不应跳到极远轨道
    // ---------------------------------------------------------------------

    @Test
    public void middleDragVerticalLandsOnMouseTrackAndIsStable() throws Exception {
        // 用 yToTrackIndex 反推：鼠标停在轨道 2 的带内（mouseLocalY≈200 → index 2）
        Track t0 = timeline.getTrack(0);
        timeline.getTrack(3); // 确保轨道存在
        Segment s = newSeg(1000_000L);
        SegActor actor = place(t0, s, 0, 1000_000L);

        float firstX = actor.getWidth() / 2;
        float firstY = view.trackHeight / 2;
        setDragSide(actor, DragSide.MIDDLE);
        tl.initDrag(actor, firstX, firstY);

        // 目标的 yToTrackIndex(targetY + trackHeight/2) == 2
        float mouseLocalY = 200f;
        float mouseLocalX = actor.getX() + firstX;
        tl.segDrag(actor, mouseLocalX - actor.getX(), mouseLocalY - actor.getY());

        // 应恰好落到轨道 2，而不是越跳越远
        assertEquals(2, s.getTrack().index);

        // 鼠标不动，重建喂回：轨道必须稳定（幂等）
        for (int i = 0; i < 5; i++) {
            rebuildAndFeed(actor, mouseLocalX, mouseLocalY);
            assertEquals(2, s.getTrack().index);
        }
    }

    // ---------------------------------------------------------------------
    // 边缘裁切：小幅拖拽不应放大，且不改变 origin
    // ---------------------------------------------------------------------

    @Test
    public void frontResizeMovesStartBySameDeltaKeepsOriginAndIsStable() throws Exception {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(1000_000L);
        s.setOrigin(5_000_000L);
        SegActor actor = place(t0, s, 0, 1000_000L);

        setDragSide(actor, DragSide.FRONT);
        tl.initDrag(actor, 5f, view.trackHeight / 2);

        float newFrontLocalX = 50f; // 起点右移 50px=50ms
        tl.segDrag(actor, newFrontLocalX, view.trackHeight / 2);
        assertEquals(Range.closedOpen(50_000L, 1000_000L), s.getRange());
        assertEquals(5_000_000L, s.getOrigin());

        // 鼠标不动（停在裁切后的新起点 absX(50000)），重建喂回：起点与 origin 都必须稳定
        for (int i = 0; i < 5; i++) {
            rebuildAndFeed(actor, absX(50_000L), view.trackHeight / 2);
            assertEquals(Range.closedOpen(50_000L, 1000_000L), s.getRange());
            assertEquals(5_000_000L, s.getOrigin());
        }
    }

    @Test
    public void behindResizeMovesEndBySameDeltaKeepsOriginAndIsStable() throws Exception {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(1000_000L);
        s.setOrigin(5_000_000L);
        SegActor actor = place(t0, s, 0, 1000_000L);

        setDragSide(actor, DragSide.BEHIND);
        tl.initDrag(actor, actor.getWidth(), view.trackHeight / 2);

        float newWidth = actor.getWidth() + 60f; // 终点右移 60px=60ms
        tl.segDrag(actor, newWidth, view.trackHeight / 2);
        assertEquals(Range.closedOpen(0L, 1000_000L + 60_000L), s.getRange());
        assertEquals(5_000_000L, s.getOrigin());

        // 鼠标不动，重建喂回：终点与 origin 都必须稳定
        for (int i = 0; i < 5; i++) {
            rebuildAndFeed(actor, absX(1000_000L + 60_000L), view.trackHeight / 2);
            assertEquals(Range.closedOpen(0L, 1000_000L + 60_000L), s.getRange());
            assertEquals(5_000_000L, s.getOrigin());
        }
    }
}
