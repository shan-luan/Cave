package com.lomekwi.cave.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Range;
import com.lomekwi.cave.project.TestProject;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

/**
 * 拖拽 API 的模型级测试。
 * 目标：验证截断式（clamp）的探测/应用语义：执行方法一次调用即把 deltaTime
 * 同向截断到最大可用偏移量并应用，返回实际应用的偏移量（0 = 未移动）。
 *
 * 覆盖：
 *  - add/remove 基础放置与覆盖
 *  - move（整体平移）：无阻碍时精确落位 + origin 同步；有阻碍时截断到与障碍贴合或原地不动
 *  - setStart/setEnd（头/尾裁切）：只动对应端点；越界/受阻时截断到边界
 *  - split：一分为二，两侧区间正确
 *  - Undo/redo：统一经 UndoManager 还原/重放
 */
public class TrackDragTest extends GdxTestBase {

    private TestProject project;
    private Timeline timeline;

    @Before
    public void setUp() {
        project = new TestProject();
        timeline = project.timeline;
    }

    private Segment newSeg(long duration) {
        return new Segment(new TestSource(duration));
    }

    private static Range<Long> rng(long lo, long hi) {
        return Range.closedOpen(lo, hi);
    }

    // ---------------------------------------------------------------------
    // 添加 / 移除
    // ---------------------------------------------------------------------

    @Test
    public void addPlacesSegmentAndSetsRangeAndTrack() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        assertSame(s, t0.get(50));
        assertEquals(rng(0, 100), s.getRange());
        assertSame(t0, s.getTrack());
    }

    @Test
    public void tryAddAtOccupiedRangeDoesNotReplace() {
        Track t0 = timeline.getTrack(0);
        Segment a = newSeg(100);
        Segment b = newSeg(100);
        timeline.override(t0, a, rng(0, 100));

        long shift = timeline.tryAdd(t0, b, rng(0, 100));

        assertTrue(shift != 0);
        assertSame(a, t0.get(50));
        assertEquals(1, countOn(t0));
    }

    @Test
    public void removeSegmentRemovesIt() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        timeline.remove(s);
        assertTrue(t0.get(50) == null);
        assertTrue(t0.isEmpty());
    }

    @Test
    public void removeCollectionRemovesAll() {
        Track t0 = timeline.getTrack(0);
        Segment a = newSeg(100);
        Segment b = newSeg(100);
        timeline.override(t0, a, rng(0, 100));
        timeline.override(t0, b, rng(500, 600));

        timeline.remove(List.of(a, b));
        assertTrue(t0.isEmpty());
    }

    // ---------------------------------------------------------------------
    // move（整体平移）
    // ---------------------------------------------------------------------

    @Test
    public void moveSingleSegmentByTimePreservesDurationAndOffsetsOrigin() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        s.setOrigin(1000);
        timeline.override(t0, s, rng(0, 100));

        long applied = timeline.moveTime(List.of(s), 200);

        assertEquals(200, applied);
        assertEquals(rng(200, 300), s.getRange());
        assertSame(t0, s.getTrack());
        assertEquals(1200, s.getOrigin());
    }

    @Test
    public void moveSingleSegmentAcrossTracks() {
        Track t0 = timeline.getTrack(0);
        Track t1 = timeline.getTrack(1);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        int applied = timeline.moveTrack(List.of(s), 1);

        // 返回实际落位的轨道偏移
        assertEquals(1, applied);
        assertSame(t1, s.getTrack());
        assertEquals(rng(0, 100), s.getRange());
        assertTrue(t0.isEmpty());
        assertSame(s, t1.get(50));
    }

    @Test
    public void moveMultiSelectMovesAllMembersTogether() {
        Track t0 = timeline.getTrack(0);
        Track t1 = timeline.getTrack(1);
        Segment a = newSeg(100);
        Segment b = newSeg(100);
        timeline.override(t0, a, rng(0, 100));
        timeline.override(t1, b, rng(500, 600));

        long applied = timeline.moveTime(List.of(a, b), 1000);

        assertEquals(1000, applied);
        assertEquals(rng(1000, 1100), a.getRange());
        assertEquals(rng(1500, 1600), b.getRange());
        assertSame(t0, a.getTrack());
        assertSame(t1, b.getTrack());
    }

    @Test
    public void moveTimeBackwardClampsAtZeroForLeadingMember() {
        Track t0 = timeline.getTrack(0);
        Segment a = newSeg(100);
        Segment b = newSeg(100);
        timeline.override(t0, a, rng(50, 150));
        timeline.override(t0, b, rng(200, 300));

        // 锚定 b 拖到 0（整体偏移 -200）：组内更靠前的 a 起点 50 只能到 0，
        // 整组偏移被夹到 -50，a/b 都不能越过时间轴 0
        long applied = timeline.moveTime(List.of(a, b), -200);

        assertEquals(-50, applied);
        assertEquals(rng(0, 100), a.getRange());
        assertEquals(rng(150, 250), b.getRange());
    }

    @Test
    public void moveIntoOccupiedSpotOnSameTrackDoesNotApply() {
        Track t0 = timeline.getTrack(0);
        Segment mover = newSeg(100);
        Segment obstacle = newSeg(1000);
        timeline.override(t0, mover, rng(0, 100));
        // 障碍占据 [100, 1100)，把 mover 挪到 [150,250) 会撞上它
        timeline.override(t0, obstacle, rng(100, 1100));

        // 右侧紧贴障碍：最大可用偏移为 0，完全无法移动
        long applied = timeline.moveTime(List.of(mover), 150);

        assertEquals(0, applied);
        assertEquals(rng(0, 100), mover.getRange());
        assertSame(obstacle, t0.get(200));
    }

    @Test
    public void moveTimeClampsAtObstacleEdge() {
        Track t0 = timeline.getTrack(0);
        Segment mover = newSeg(100);
        Segment obstacle = newSeg(100);
        timeline.override(t0, mover, rng(0, 100));
        timeline.override(t0, obstacle, rng(300, 400));

        // 请求 +250：最多移到与障碍贴合（+200），一次调用直接应用
        long applied = timeline.moveTime(List.of(mover), 250);
        assertEquals(200, applied);
        assertEquals(rng(200, 300), mover.getRange());
        assertSame(obstacle, t0.get(350));

        // 请求在可用范围内时全额应用（返回带符号的实际偏移）
        assertEquals(-50, timeline.moveTime(List.of(mover), -50));
        assertEquals(rng(150, 250), mover.getRange());
    }

    @Test
    public void moveAcrossTrackIntoOccupiedSpotDoesNotApply() {
        Track t0 = timeline.getTrack(0);
        Track t1 = timeline.getTrack(1);
        Segment mover = newSeg(100);
        Segment obstacle = newSeg(1000);
        timeline.override(t0, mover, rng(0, 100));
        timeline.override(t1, obstacle, rng(0, 1000)); // 目标轨道同区间被占据

        // 目标轨道被占据且方向上无更近的可落点：偏移截断为 0，保持原位
        int applied = timeline.moveTrack(List.of(mover), 1);

        assertEquals(0, applied);
        assertSame(t0, mover.getTrack());
        assertEquals(rng(0, 100), mover.getRange());
        assertSame(obstacle, t1.get(50));
    }

    // ---------------------------------------------------------------------
    // setStart / setEnd（头/尾裁切）
    // ---------------------------------------------------------------------

    @Test
    public void setStartSlidesFrontKeepsEndFixed() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        long applied = timeline.setStart(List.of(s), 30);

        assertEquals(30, applied);
        assertEquals(rng(30, 100), s.getRange());
        assertSame(t0, s.getTrack());
        // 裁切不改 origin
        assertEquals(0, s.getOrigin());
    }

    @Test
    public void setEndSlidesBackKeepsStartFixed() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 50));

        long applied = timeline.setEnd(List.of(s), 50);

        assertEquals(50, applied);
        assertEquals(rng(0, 100), s.getRange());
    }

    @Test
    public void setEndShrinksBackwards() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        long applied = timeline.setEnd(List.of(s), -20);

        assertEquals(-20, applied);
        assertEquals(rng(0, 80), s.getRange());
    }

    @Test
    public void setStartIntoOccupiedSpotDoesNotApply() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        Segment obstacle = newSeg(100);
        timeline.override(t0, obstacle, rng(0, 100)); // 左侧障碍占住 [0,100)
        timeline.override(t0, s, rng(100, 200));      // 把 s 起点往左推到 50 会撞上它

        // 左侧紧贴障碍：最大可用偏移为 0，完全无法移动
        long applied = timeline.setStart(List.of(s), -50);

        assertEquals(0, applied);
        assertEquals(rng(100, 200), s.getRange());
        assertSame(obstacle, t0.get(50));
    }

    @Test
    public void setStartBackwardClampsToNearestObstacleEdge() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(200);
        Segment obstacle = newSeg(50);
        timeline.override(t0, s, rng(50, 100));      // 前端已被裁切，origin=0 → minStart=0
        timeline.override(t0, obstacle, rng(0, 30)); // 占住 [0,30)

        // 想把前端拖到 -10（delta=-60）：minStart 允许回到 0，但 obstacle 终点 30 更近，
        // 一次调用直接左移到与障碍贴合（起点 30，偏移 -20）
        long applied = timeline.setStart(List.of(s), -60);

        assertEquals(-20, applied);
        assertEquals(rng(30, 100), s.getRange());
        assertSame(obstacle, t0.get(10));
    }

    @Test
    public void setEndForwardClampsToNearestObstacleEdge() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        Segment obstacle = newSeg(40);
        timeline.override(t0, s, rng(0, 50));
        timeline.override(t0, obstacle, rng(80, 120)); // 占住 [80,120)

        // 想把尾端拖到 250（delta=200）：maxEnd=100 与 obstacle 起点 80 相比 80 更近，
        // 一次调用直接右移到与障碍贴合（终点 80，偏移 +30）
        long applied = timeline.setEnd(List.of(s), 200);

        assertEquals(30, applied);
        assertEquals(rng(0, 80), s.getRange());
        assertSame(obstacle, t0.get(90));
    }

    @Test
    public void groupSetEndAdjacentMembersDoNotOverlap() {
        Track t0 = timeline.getTrack(0);
        Segment a = newSeg(100);
        Segment b = newSeg(100);
        a.setOrigin(500); // 允许尾端伸展
        b.setOrigin(600);
        timeline.override(t0, a, rng(0, 100));
        timeline.override(t0, b, rng(100, 200)); // 与 a 相邻

        // 伸展被后一个成员的起点挡住：整组偏移截断为 0，模型不变
        long applied = timeline.setEnd(List.of(a, b), 50);
        assertEquals(0, applied);
        assertEquals(rng(0, 100), a.getRange());
        assertEquals(rng(100, 200), b.getRange());
        assertSame(a, t0.get(50));
        assertSame(b, t0.get(150));

        // 再次请求仍截断为 0，成员仍不重叠
        assertEquals(0, timeline.setEnd(List.of(a, b), 50));
        assertEquals(rng(0, 100), a.getRange());
        assertEquals(rng(100, 200), b.getRange());
    }

    @Test
    public void groupSetStartAdjacentMembersDoNotOverlap() {
        Track t0 = timeline.getTrack(0);
        Segment a = newSeg(100);
        Segment b = newSeg(100);
        timeline.override(t0, a, rng(100, 200));
        timeline.override(t0, b, rng(200, 300)); // 与 a 相邻

        // 前移被前一个成员的终点挡住：整组偏移截断为 0，模型不变
        long applied = timeline.setStart(List.of(a, b), -50);
        assertEquals(0, applied);
        assertEquals(rng(100, 200), a.getRange());
        assertEquals(rng(200, 300), b.getRange());
        assertSame(a, t0.get(100));
        assertSame(b, t0.get(250));

        // 再次请求仍截断为 0，成员仍不重叠
        assertEquals(0, timeline.setStart(List.of(a, b), -50));
        assertEquals(rng(100, 200), a.getRange());
        assertEquals(rng(200, 300), b.getRange());
    }

    // ---------------------------------------------------------------------
    // snapTime（吸附点获取）
    // ---------------------------------------------------------------------

    @Test
    public void snapTimeSnapsToNearestEdgeWithinThreshold() {
        Track t0 = timeline.getTrack(0);
        Segment a = newSeg(100);
        Segment b = newSeg(100);
        timeline.override(t0, a, rng(100, 200));
        timeline.override(t0, b, rng(400, 500));

        // 距 a 起点 100 仅 5：吸附到 100
        assertEquals(100, timeline.snapTime(105, 10, Set.of()));
        // 距 b 终点 500 仅 3：吸附到 500
        assertEquals(500, timeline.snapTime(497, 10, Set.of()));
        // 阈值内无更近端点：返回原值
        assertEquals(300, timeline.snapTime(300, 10, Set.of()));
        // 阈值外：不吸附
        assertEquals(120, timeline.snapTime(120, 10, Set.of()));
    }

    @Test
    public void snapTimeIgnoresGivenSegmentsAndSnapsToZero() {
        Track t0 = timeline.getTrack(0);
        Segment a = newSeg(100);
        timeline.override(t0, a, rng(100, 200));

        // ignore 中的片段不参与吸附
        assertEquals(150, timeline.snapTime(150, 10, Set.of(a)));
        // 距 0 比距任何端点都近：吸附到 0
        assertEquals(0, timeline.snapTime(5, 10, Set.of()));
    }

    // ---------------------------------------------------------------------
    // 分割
    // ---------------------------------------------------------------------

    @Test
    public void splitSplitsSegmentIntoTwoHalvesWithCorrectRanges() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        timeline.split(t0, 40);

        // 原始片段被压缩到左半
        assertEquals(rng(0, 40), s.getRange());
        assertSame(s, t0.get(20));
        // 右半是不相同的另一个片段
        Segment right = t0.get(60);
        assertNotSame(s, right);
        assertEquals(rng(40, 100), right.getRange());
        assertSame(t0, right.getTrack());
        // 轨道上总共 2 个片段
        assertEquals(2, countOn(t0));
    }

    // ---------------------------------------------------------------------
    // Undo / redo（与上个提交一致的命令语义）
    // ---------------------------------------------------------------------

    @Test
    public void undoRedoMoveSegCommandRestoresState() {
        Track t0 = timeline.getTrack(0);
        Track t1 = timeline.getTrack(1);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));
        s.setOrigin(1000);

        project.undoManager.execute(new UndoManager.MoveSegCommand(t0, t1, s, rng(0, 100), rng(50, 150)));
        assertEquals(rng(50, 150), s.getRange());
        assertSame(t1, s.getTrack());
        assertEquals(1050, s.getOrigin());

        project.undoManager.undo();
        assertSame(t0, s.getTrack());
        assertEquals(rng(0, 100), s.getRange());
        assertEquals(1000, s.getOrigin());

        project.undoManager.redo();
        assertSame(t1, s.getTrack());
        assertEquals(rng(50, 150), s.getRange());
        assertEquals(1050, s.getOrigin());
    }

    @Test
    public void undoRedoResizeSegCommandRestoresState() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        project.undoManager.execute(new UndoManager.ResizeSegCommand(t0, s, rng(0, 100), rng(30, 100)));
        assertEquals(rng(30, 100), s.getRange());

        project.undoManager.undo();
        assertEquals(rng(0, 100), s.getRange());

        project.undoManager.redo();
        assertEquals(rng(30, 100), s.getRange());
    }

    @Test
    public void undoRedoSplitSegCommandRestoresState() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));
        Segment right = s.duplicate();

        project.undoManager.execute(new UndoManager.SplitSegCommand(t0, s, rng(0, 100), right, 40));

        // execute 已应用分割：一分为二
        assertEquals(rng(0, 40), s.getRange());
        assertEquals(rng(40, 100), right.getRange());
        assertEquals(2, countOn(t0));

        project.undoManager.undo();
        assertEquals(rng(0, 100), s.getRange());
        assertEquals(1, countOn(t0));
        assertSame(s, t0.get(50));
    }

    @Test
    public void compoundMoveUndoRestoresMultipleSegments() {
        Track t0 = timeline.getTrack(0);
        Track t1 = timeline.getTrack(1);
        Segment a = newSeg(100);
        a.setOrigin(1000);
        Segment b = newSeg(100);
        b.setOrigin(2000);
        timeline.override(t0, a, rng(0, 100));
        timeline.override(t1, b, rng(500, 600));

        // 模拟 UI 拖拽：record 期间直接改动模型，关闭时合并为一条复合命令
        try (var h = timeline.record()) {
            timeline.moveTime(List.of(a, b), 1000);
        }
        assertEquals(rng(1000, 1100), a.getRange());
        assertEquals(2000, a.getOrigin());
        assertEquals(rng(1500, 1600), b.getRange());
        assertEquals(3000, b.getOrigin());

        project.undoManager.undo();
        assertEquals(rng(0, 100), a.getRange());
        assertEquals(1000, a.getOrigin());
        assertEquals(rng(500, 600), b.getRange());
        assertEquals(2000, b.getOrigin());
    }

    // ---------------------------------------------------------------------

    private int countOn(Track track) {
        int c = 0;
        for (Segment ignored : track) c++;
        return c;
    }
}
