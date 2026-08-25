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

/**
 * 拖拽 API 的模型级测试。
 * 目标：重构后的 Timeline/Track 在"正常拖拽"场景下的行为与重构前（HEAD）等效。
 *
 * 覆盖：
 *  - add/remove 基础放置与覆盖
 *  - move（整体平移）：无阻碍时精确落位 + origin 同步；有阻碍时不落位且返回非零修正量
 *  - setStart/setEnd（头/尾裁切）：只动对应端点；有阻碍时返回非零修正量
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
    // add / remove
    // ---------------------------------------------------------------------

    @Test
    public void addPlacesSegmentAndSetsRangeAndTrack() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        assertSame(s, t0.getEntry(50).getValue());
        assertEquals(rng(0, 100), s.getRange());
        assertSame(t0, s.getTrack());
    }

    @Test
    public void addOverwritesExistingSegmentAtSameRange() {
        Track t0 = timeline.getTrack(0);
        Segment a = newSeg(100);
        Segment b = newSeg(100);
        timeline.override(t0, a, rng(0, 100));
        timeline.override(t0, b, rng(0, 100));

        assertSame(b, t0.getEntry(50).getValue());
        assertEquals(1, countOn(t0));
    }

    @Test
    public void removeSegmentRemovesIt() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        timeline.remove(s);
        assertTrue(t0.getEntry(50) == null);
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

        long fix = timeline.move(List.of(s), 200, 0);

        assertEquals(0, fix);
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

        long fix = timeline.move(List.of(s), 0, 1);

        assertEquals(0, fix);
        assertSame(t1, s.getTrack());
        assertEquals(rng(0, 100), s.getRange());
        assertTrue(t0.isEmpty());
        assertSame(s, t1.getEntry(50).getValue());
    }

    @Test
    public void moveMultiSelectMovesAllMembersTogether() {
        Track t0 = timeline.getTrack(0);
        Track t1 = timeline.getTrack(1);
        Segment a = newSeg(100);
        Segment b = newSeg(100);
        timeline.override(t0, a, rng(0, 100));
        timeline.override(t1, b, rng(500, 600));

        long fix = timeline.move(List.of(a, b), 1000, 0);

        assertEquals(0, fix);
        assertEquals(rng(1000, 1100), a.getRange());
        assertEquals(rng(1500, 1600), b.getRange());
        assertSame(t0, a.getTrack());
        assertSame(t1, b.getTrack());
    }

    @Test
    public void moveIntoOccupiedSpotOnSameTrackDoesNotApply() {
        Track t0 = timeline.getTrack(0);
        Segment mover = newSeg(100);
        Segment obstacle = newSeg(1000);
        timeline.override(t0, mover, rng(0, 100));
        // 障碍占据 [100, 1100)，把 mover 挪到 [150,250) 会撞上它
        timeline.override(t0, obstacle, rng(100, 1100));

        long fix = timeline.move(List.of(mover), 150, 0);

        assertTrue(fix != 0);
        // 未发生移动：mover 仍在原处
        assertEquals(rng(0, 100), mover.getRange());
        assertSame(obstacle, t0.getEntry(200).getValue());
    }

    @Test
    public void moveAcrossTrackIntoOccupiedSpotDoesNotApply() {
        Track t0 = timeline.getTrack(0);
        Track t1 = timeline.getTrack(1);
        Segment mover = newSeg(100);
        Segment obstacle = newSeg(1000);
        timeline.override(t0, mover, rng(0, 100));
        timeline.override(t1, obstacle, rng(0, 1000)); // 目标轨道同区间被占据

        long fix = timeline.move(List.of(mover), 0, 1);

        assertTrue(fix != 0);
        assertSame(t0, mover.getTrack());
        assertEquals(rng(0, 100), mover.getRange());
        assertSame(obstacle, t1.getEntry(50).getValue());
    }

    // ---------------------------------------------------------------------
    // setStart / setEnd（头/尾裁切）
    // ---------------------------------------------------------------------

    @Test
    public void setStartSlidesFrontKeepsEndFixed() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        long fix = timeline.setStart(List.of(s), 30);

        assertEquals(0, fix);
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

        long fix = timeline.setEnd(List.of(s), 50);

        assertEquals(0, fix);
        assertEquals(rng(0, 100), s.getRange());
    }

    @Test
    public void setEndShrinksBackwards() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        long fix = timeline.setEnd(List.of(s), -20);

        assertEquals(0, fix);
        assertEquals(rng(0, 80), s.getRange());
    }

    @Test
    public void setStartIntoOccupiedSpotDoesNotApply() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        Segment obstacle = newSeg(100);
        timeline.override(t0, s, rng(0, 100));
        timeline.override(t0, obstacle, rng(50, 150)); // 把 s 起点推到 50 会撞上

        long fix = timeline.setStart(List.of(s), 50);

        assertTrue(fix != 0);
        assertEquals(rng(0, 100), s.getRange());
        assertSame(obstacle, t0.getEntry(60).getValue());
    }

    // ---------------------------------------------------------------------
    // split
    // ---------------------------------------------------------------------

    @Test
    public void splitSplitsSegmentIntoTwoHalvesWithCorrectRanges() {
        Track t0 = timeline.getTrack(0);
        Segment s = newSeg(100);
        timeline.override(t0, s, rng(0, 100));

        timeline.split(t0, 40);

        // 原始片段被压缩到左半
        assertEquals(rng(0, 40), s.getRange());
        assertSame(s, t0.getEntry(20).getValue());
        // 右半是不相同的另一个片段
        Segment right = t0.getEntry(60).getValue();
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
        assertSame(s, t0.getEntry(50).getValue());
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
            timeline.move(List.of(a, b), 1000, 0);
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
