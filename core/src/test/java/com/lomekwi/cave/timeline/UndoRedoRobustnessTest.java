package com.lomekwi.cave.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Range;
import com.lomekwi.cave.project.TestProject;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 时间线撤销/重做系统的鲁棒性测试：
 * 新建时间线，随机生成大量模拟片段、随机组合为组，再随机执行各种"拖拽"（整体移动、
 * 头/尾裁切、分割、删除、新增）。在随机时刻对时间线做序列化快照，继续随机操作后
 * 把快照之后产生的全部命令撤销掉，断言撤销回来的时间线与快照相等（再重做一遍，
 * 断言与操作后的状态相等）。
 *
 * 依赖 {@link Timeline#equals} / {@link Track#equals} 做结构化比较：按轨道、按区间
 * 逐项比对片段（源类型/时长、origin、区间），不依赖对象身份，因此能直接和深拷贝的
 * 快照对比。
 */
public class UndoRedoRobustnessTest extends GdxTestBase {

    /** 时间轴总跨度（µs） */
    private static final long SPAN = 200_000;
    private static final long MIN_DURATION = 1_000;
    private static final long MAX_DURATION = 15_000;
    private static final int TRACK_COUNT = 4;
    private static final int SEGMENT_COUNT = 60;

    private TestProject project;
    private Timeline timeline;
    private final List<SegmentGroup> groups = new ArrayList<>();

    @Test
    public void undoAfterRandomDragsRestoresSnapshot() {
        // 多个种子多轮运行，覆盖不同的随机布局与操作序列
        for (int seed : new int[]{20240101, 20240202, 20240303, 12345678, 9876554,999999,888888888,0xcafebabe,0xabcdef}) {
            runScenario(seed);
        }
    }

    private void runScenario(int seed) {
        project = new TestProject();
        timeline = project.timeline;
        groups.clear();
        Random rnd = new Random(seed);

        // 1. 随机创建大量片段并摆放到不重叠的区间
        createRandomSegments(rnd);

        // 2. 随机把片段组合为组
        randomGrouping(rnd);

        // 3. 随机拖拽若干步（全部可撤销）
        int preSteps = 20 + rnd.nextInt(21);
        for (int i = 0; i < preSteps; i++) {
            randomDrag(rnd);
        }

        // 4. 在随机时刻做序列化快照。
        //    同时清空命令历史：快照之后每执行一步就压入一条命令，
        //    之后"撤销同样的步数"即等价于撤销快照之后的全部操作，
        //    避免跨快照边界时同类型命令在撤销栈顶合并而污染步数统计。
        Timeline snapshot = timeline.duplicate();
        project.undoManager.clear();

        int postSteps = 5 + rnd.nextInt(16);
        for (int i = 0; i < postSteps; i++) {
            randomDrag(rnd);
        }

        // 再拍一份快照，稍后用于验证 redo
        Timeline afterOps = timeline.duplicate();

        // 5. 撤销快照之后执行的全部命令，时间线应回到快照状态
        int undoCalls = 0;
        while (project.undoManager.canUndo()) {
            project.undoManager.undo();
            undoCalls++;
        }
        assertTrue("快照后至少应产生一条可撤销的命令", undoCalls > 0);
        assertEquals("撤销后应恢复到序列化快照的状态", snapshot, timeline);

        // 6. 重做全部命令，时间线应回到操作后的状态
        int redoCalls = 0;
        while (project.undoManager.canRedo()) {
            project.undoManager.redo();
            redoCalls++;
        }
        assertEquals("重做后应恢复到操作后的状态", afterOps, timeline);
        assertEquals("撤销与重做的步数应一致", undoCalls, redoCalls);
    }

    // ---------------------------------------------------------------------
    // 随机布局
    // ---------------------------------------------------------------------

    private void createRandomSegments(Random rnd) {
        List<List<Range<Long>>> occupied = new ArrayList<>();
        for (int i = 0; i < TRACK_COUNT; i++) {
            occupied.add(new ArrayList<>());
        }
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            long duration = MIN_DURATION + rnd.nextLong(MAX_DURATION - MIN_DURATION + 1);
            int trackIndex = rnd.nextInt(TRACK_COUNT);
            Range<Long> range = pickFreeRange(rnd, occupied.get(trackIndex), duration);
            if (range == null) continue; // 该轨道放不下就跳过
            Segment seg = new Segment(new TestSource(duration));
            seg.setOrigin(rnd.nextLong(SPAN * 2));
            timeline.tryAdd(timeline.getTrack(trackIndex), seg, range);
        }
    }

    private Range<Long> pickFreeRange(Random rnd, List<Range<Long>> occupied, long duration) {
        for (int attempt = 0; attempt < 50; attempt++) {
            long start = rnd.nextLong(SPAN - duration + 1);
            Range<Long> range = Range.closedOpen(start, start + duration);
            boolean free = true;
            for (Range<Long> o : occupied) {
                if (o.isConnected(range)) {
                    free = false;
                    break;
                }
            }
            if (free) {
                occupied.add(range);
                return range;
            }
        }
        return null;
    }

    private void randomGrouping(Random rnd) {
        for (Track track : timeline.getTracks()) {
            for (Segment seg : track) {
                if (rnd.nextFloat() < 0.35f) {
                    SegmentGroup group;
                    if (!groups.isEmpty() && rnd.nextFloat() < 0.5f) {
                        group = groups.get(rnd.nextInt(groups.size()));
                    } else {
                        group = new SegmentGroup();
                        groups.add(group);
                    }
                    group.add(seg);
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // 随机拖拽（每次 = 一个 record 块 = 一条可撤销命令）
    // ---------------------------------------------------------------------

    private void randomDrag(Random rnd) {
        for (int attempt = 0; attempt < 40; attempt++) {
            long before = project.currentVersion;
            performRandomOp(rnd);
            if (project.currentVersion != before) return; // 成功记录了一步
        }
        forceChange(rnd); // 兜底：保证一定有可撤销的命令
    }

    private void performRandomOp(Random rnd) {
        List<Segment> placed = placedSegments();
        int op = rnd.nextInt(7);
        switch (op) {
            case 0, 1 -> moveOp(rnd, placed);
            case 2 -> frontResizeOp(rnd, placed);
            case 3 -> behindResizeOp(rnd, placed);
            case 4 -> splitOp(rnd, placed);
            case 5 -> removeOp(rnd, placed);
            default -> addOp(rnd);
        }
    }

    /** 与 UI 一致：拖拽锚点片段时，其所在组的成员会一起被操作。 */
    private List<Segment> dragMembers(Segment segment) {
        SegmentGroup group = segment.getGroup();
        return group != null ? List.copyOf(group) : List.of(segment);
    }

    private void moveOp(Random rnd, List<Segment> placed) {
        if (placed.isEmpty()) return;
        List<Segment> members = dragMembers(placed.get(rnd.nextInt(placed.size())));
        int minIdx = Integer.MAX_VALUE;
        for (Segment m : members) {
            minIdx = Math.min(minIdx, m.getTrack().index);
        }
        int trackDelta = rnd.nextInt(3) - 1; // -1..1
        if (minIdx + trackDelta < 0) trackDelta = 0;
        long deltaTime = rnd.nextLong(SPAN / 2) - SPAN / 4;
        try (var h = timeline.record()) {
            // 与 UI 一致：时间维度可放置后再移动轨道
            if (timeline.moveTime(members, deltaTime) == 0) {
                timeline.moveTrack(members, trackDelta);
            }
        }
    }

    private void frontResizeOp(Random rnd, List<Segment> placed) {
        if (placed.isEmpty()) return;
        List<Segment> members = dragMembers(placed.get(rnd.nextInt(placed.size())));
        long delta = rnd.nextLong(MAX_DURATION) - MAX_DURATION / 2;
        try (var h = timeline.record()) {
            timeline.setStart(members, delta);
        }
    }

    private void behindResizeOp(Random rnd, List<Segment> placed) {
        if (placed.isEmpty()) return;
        List<Segment> members = dragMembers(placed.get(rnd.nextInt(placed.size())));
        long delta = rnd.nextLong(MAX_DURATION) - MAX_DURATION / 2;
        try (var h = timeline.record()) {
            timeline.setEnd(members, delta);
        }
    }

    private void splitOp(Random rnd, List<Segment> placed) {
        if (placed.isEmpty()) return;
        Segment s = placed.get(rnd.nextInt(placed.size()));
        var r = s.getRange();
        long lo = r.lowerEndpoint();
        long hi = r.upperEndpoint();
        if (hi - lo < 2) return;
        long time = lo + 1 + rnd.nextLong(hi - lo - 1);
        try (var h = timeline.record()) {
            timeline.split(s.getTrack(), time);
        }
    }

    private void removeOp(Random rnd, List<Segment> placed) {
        if (placed.isEmpty()) return;
        List<Segment> members = dragMembers(placed.get(rnd.nextInt(placed.size())));
        try (var h = timeline.record()) {
            timeline.remove(members);
        }
    }

    private void addOp(Random rnd) {
        long duration = MIN_DURATION + rnd.nextLong(MAX_DURATION - MIN_DURATION + 1);
        Track track = timeline.getTrack(rnd.nextInt(TRACK_COUNT));
        for (int attempt = 0; attempt < 30; attempt++) {
            long start = rnd.nextLong(Math.max(1, SPAN - duration));
            Range<Long> range = Range.closedOpen(start, start + duration);
            if (track.isFree(range, Set.of())) {
                Segment seg = new Segment(new TestSource(duration));
                seg.setOrigin(rnd.nextLong(SPAN));
                try (var h = timeline.record()) {
                    timeline.tryAdd(track, seg, range);
                }
                return;
            }
        }
    }

    private void forceChange(Random rnd) {
        List<Segment> placed = placedSegments();
        if (placed.isEmpty()) {
            addOp(rnd); // 全空时补一个片段；仍失败则放弃这一步
            return;
        }
        List<Segment> members = dragMembers(placed.get(rnd.nextInt(placed.size())));
        try (var h = timeline.record()) {
            timeline.remove(members);
        }
    }

    private List<Segment> placedSegments() {
        List<Segment> out = new ArrayList<>();
        for (Track track : timeline.getTracks()) {
            for (Segment s : track) {
                out.add(s);
            }
        }
        return out;
    }
}
