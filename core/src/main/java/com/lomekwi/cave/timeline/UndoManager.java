package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Node;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.num.NumFrame;
import com.lomekwi.cave.pipeline.image.TransNode;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.project.ProjectDirtyChangedEvent;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

@SuppressWarnings({"rawtypes", "unchecked"})
@NullMarked
public class UndoManager {
    private static final int MAX_UNDO = 100;
    private final Deque<UndoableCommand> undoStack = new ArrayDeque<>();
    private final Deque<UndoableCommand> redoStack = new ArrayDeque<>();
    private final transient Project project;

    public UndoManager(Project project) {
        this.project = project;
    }

    public void execute(UndoableCommand command) {
        boolean wasDirty = project.isDirty();
        project.currentVersion++;
        command.redo();
        push(command);
        if (wasDirty != project.isDirty()) {
            project.projEventBus.post(ProjectDirtyChangedEvent.INSTANCE);
        }
    }

    public void record(UndoableCommand command) {
        boolean wasDirty = project.isDirty();
        project.currentVersion++;
        push(command);
        if (wasDirty != project.isDirty()) {
            project.projEventBus.post(ProjectDirtyChangedEvent.INSTANCE);
        }
    }

    private void push(UndoableCommand command) {
        // 与栈顶同类型的可合并命令直接合并
        if (!undoStack.isEmpty() && command instanceof MergeableCommand mc) {
            var top = undoStack.peek();
            if (top.getClass() == command.getClass() && top instanceof MergeableCommand topMc) {
                if (topMc.merge(command)) {
                    redoStack.clear();
                    if (undoStack.size() > MAX_UNDO) {
                        undoStack.removeLast();
                    }
                    return;
                }
            }
        }
        undoStack.push(command);
        redoStack.clear();
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        boolean wasDirty = project.isDirty();
        project.currentVersion--;
        var command = undoStack.pop();
        command.undo();
        redoStack.push(command);
        if (wasDirty != project.isDirty()) {
            project.projEventBus.post(ProjectDirtyChangedEvent.INSTANCE);
        }
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        boolean wasDirty = project.isDirty();
        project.currentVersion++;
        var command = redoStack.pop();
        command.redo();
        undoStack.push(command);
        if (wasDirty != project.isDirty()) {
            project.projEventBus.post(ProjectDirtyChangedEvent.INSTANCE);
        }
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public interface UndoableCommand {
        void undo();
        void redo();
    }

    /**
     * 可合并命令：同类型命令可合并为一个，避免栈中存在连续的同类型记录。
     */
    public interface MergeableCommand extends UndoableCommand {
        /**
         * 将 other 合并到当前命令中。合并后，undo() 应能撤销两者的效果，
         * redo() 应能重做合并后的效果。
         * @return true 表示合并成功
         */
        boolean merge(UndoableCommand other);
    }

    // ──────────────── 单片段命令（保留向后兼容） ────────────────

    public record AddSegCommand(Track track, Segment segment, Range<Long> range) implements UndoableCommand {
        @Override
        public void undo() {
            track.remove(segment);
        }

        @Override
        public void redo() {
            track.override(segment, range);
        }
    }

    public record RemoveSegCommand(Track track, Segment segment, Range<Long> range, SegmentGroup group) implements UndoableCommand {
        public RemoveSegCommand(Track track, Segment segment, Range<Long> range) {
            this(track, segment, range, null);
        }

        @Override
        public void undo() {
            track.override(segment, range);
            if (group != null) group.add(segment);
        }

        @Override
        public void redo() {
            track.remove(segment);
            if (group != null) group.remove(segment);
        }
    }

    public record ResizeSegCommand(Track track, Segment segment, Range<Long> oldRange, Range<Long> newRange) implements UndoableCommand {
        @Override
        public void undo() {
            track.remove(segment);
            track.override(segment, oldRange);
        }

        @Override
        public void redo() {
            track.remove(segment);
            track.override(segment, newRange);
        }
    }

    public record MoveSegCommand(Track fromTrack, Track toTrack, Segment segment, Range<Long> oldRange, Range<Long> newRange) implements UndoableCommand {
        @Override
        public void undo() {
            toTrack.remove(segment);
            fromTrack.override(segment, oldRange);
            segment.offsetOrigin(oldRange.lowerEndpoint() - newRange.lowerEndpoint());
        }

        @Override
        public void redo() {
            fromTrack.remove(segment);
            toTrack.override(segment, newRange);
            segment.offsetOrigin(newRange.lowerEndpoint() - oldRange.lowerEndpoint());
        }
    }

    public record SplitSegCommand(Track track, Segment originalSeg, Range<Long> originalRange, Segment newSeg, long splitTime) implements UndoableCommand {
        @Override
        public void undo() {
            track.remove(originalSeg);
            track.remove(newSeg);
            track.override(originalSeg, originalRange);
        }

        @Override
        public void redo() {
            track.remove(originalSeg);
            track.remove(newSeg);
            track.override(originalSeg, Range.closedOpen(originalRange.lowerEndpoint(), splitTime));
            track.override(newSeg, Range.closedOpen(splitTime, originalRange.upperEndpoint()));
        }
    }

    public static class CompoundCommand implements UndoableCommand {
        private final UndoableCommand[] commands;

        public CompoundCommand(UndoableCommand... commands) {
            this.commands = commands;
        }

        @Override
        public void undo() {
            for (int i = commands.length - 1; i >= 0; i--) {
                commands[i].undo();
            }
        }

        @Override
        public void redo() {
            for (var cmd : commands) {
                cmd.redo();
            }
        }
    }

    private static List filterList(Source<?> source) {
        return source.getFilters();
    }

    // ──────────────── 批量命令（可合并） ────────────────

    /** 批量移动片段命令。合并时：同 segment 保留旧起点、更新终点；新 segment 直接追加。 */
    public static final class MoveSegsCommand implements MergeableCommand {
        private final List<MoveEntry> entries;

        public record MoveEntry(Track fromTrack, Track toTrack, Segment segment,
                                Range<Long> oldRange, Range<Long> newRange) {}

        public MoveSegsCommand(List<MoveEntry> entries) {
            this.entries = new ArrayList<>(entries);
        }

        @Override
        public void undo() {
            for (int i = entries.size() - 1; i >= 0; i--) {
                var e = entries.get(i);
                e.toTrack.remove(e.segment);
                e.fromTrack.override(e.segment, e.oldRange);
                e.segment.offsetOrigin(e.oldRange.lowerEndpoint() - e.newRange.lowerEndpoint());
            }
        }

        @Override
        public void redo() {
            for (var e : entries) {
                e.fromTrack.remove(e.segment);
                e.toTrack.override(e.segment, e.newRange);
                e.segment.offsetOrigin(e.newRange.lowerEndpoint() - e.oldRange.lowerEndpoint());
            }
        }

        @Override
        public boolean merge(UndoableCommand other) {
            if (!(other instanceof MoveSegsCommand o)) return false;
            for (var ne : o.entries) {
                boolean found = false;
                for (int i = 0; i < entries.size(); i++) {
                    var e = entries.get(i);
                    if (e.segment == ne.segment) {
                        entries.set(i, new MoveEntry(e.fromTrack, ne.toTrack, e.segment,
                            e.oldRange, ne.newRange));
                        found = true;
                        break;
                    }
                }
                if (!found) entries.add(ne);
            }
            return true;
        }
    }

    /** 批量调整片段区间命令。合并时：同 segment 保留旧区间、更新新区间；新 segment 直接追加。 */
    public static final class ResizeSegsCommand implements MergeableCommand {
        private final List<ResizeEntry> entries;

        public record ResizeEntry(Track track, Segment segment,
                                  Range<Long> oldRange, Range<Long> newRange) {}

        public ResizeSegsCommand(List<ResizeEntry> entries) {
            this.entries = new ArrayList<>(entries);
        }

        @Override
        public void undo() {
            for (int i = entries.size() - 1; i >= 0; i--) {
                var e = entries.get(i);
                e.track.remove(e.segment);
                e.track.override(e.segment, e.oldRange);
            }
        }

        @Override
        public void redo() {
            for (var e : entries) {
                e.track.remove(e.segment);
                e.track.override(e.segment, e.newRange);
            }
        }

        @Override
        public boolean merge(UndoableCommand other) {
            if (!(other instanceof ResizeSegsCommand o)) return false;
            for (var ne : o.entries) {
                boolean found = false;
                for (int i = 0; i < entries.size(); i++) {
                    var e = entries.get(i);
                    if (e.segment == ne.segment) {
                        entries.set(i, new ResizeEntry(e.track, e.segment,
                            e.oldRange, ne.newRange));
                        found = true;
                        break;
                    }
                }
                if (!found) entries.add(ne);
            }
            return true;
        }
    }

    /** 批量删除片段命令。合并时直接追加新条目（去重）。 */
    public static final class RemoveSegsCommand implements MergeableCommand {
        private final List<RemoveEntry> entries;

        public record RemoveEntry(Track track, Segment segment, Range<Long> range,
                                  @Nullable SegmentGroup group) {}

        public RemoveSegsCommand(List<RemoveEntry> entries) {
            this.entries = new ArrayList<>(entries);
        }

        @Override
        public void undo() {
            for (int i = entries.size() - 1; i >= 0; i--) {
                var e = entries.get(i);
                e.track.override(e.segment, e.range);
                if (e.group != null) e.group.add(e.segment);
            }
        }

        @Override
        public void redo() {
            for (var e : entries) {
                e.track.remove(e.segment);
                if (e.group != null) e.group.remove(e.segment);
            }
        }

        @Override
        public boolean merge(UndoableCommand other) {
            if (!(other instanceof RemoveSegsCommand o)) return false;
            outer:
            for (var ne : o.entries) {
                for (var e : entries) {
                    if (e.segment == ne.segment) continue outer;
                }
                entries.add(ne);
            }
            return true;
        }
    }
    public static void postRefresh(Source<?> source) {
        Segment seg = source.getSegment();
        if (seg != null) {
            Track track = seg.getTrack();
            if (track != null) {
                Timeline timeline = track.getTimeline();
                timeline.project.projEventBus.post(new SegmentSelectedEvent(seg, track, 1));
                timeline.project.projEventBus.post(RefreshRequestEvent.INSTANCE);
            }
        }
    }

    public record AddFilterCommand(Source<?> source, Filter<?> filter) implements UndoableCommand {
        @Override
        public void undo() {
            filterList(source).remove(filter);
            postRefresh(source);
        }

        @Override
        public void redo() {
            filterList(source).add(filter);
            postRefresh(source);
        }
    }

    public record RemoveFilterCommand(Source<?> source, Filter<?> filter, int index) implements UndoableCommand {
        @Override
        public void undo() {
            filterList(source).add(index, filter);
            postRefresh(source);
        }

        @Override
        public void redo() {
            filterList(source).remove(filter);
            postRefresh(source);
        }
    }

    public record ReorderFilterCommand(Source<?> source, Filter<?> filter, int oldIndex, int newIndex) implements UndoableCommand {
        @Override
        public void undo() {
            filterList(source).remove(filter);
            filterList(source).add(oldIndex, filter);
            postRefresh(source);
        }

        @Override
        public void redo() {
            filterList(source).remove(filter);
            filterList(source).add(newIndex, filter);
            postRefresh(source);
        }
    }

    /** 数值输入端口默认值变更命令。 */
    public record NumPortValueCommand(Node.InPort<?> port, Source<?> source, double oldValue, double newValue) implements UndoableCommand {
        @Override
        public void undo() {
            setValue(oldValue);
        }

        @Override
        public void redo() {
            setValue(newValue);
        }

        private void setValue(double v) {
            NumFrame def = (NumFrame) port.getDefaultData();
            if (def != null) def.setVal(v);
            else ((NumFrame) port.getData()).setVal(v);
            // 通知所属源刷新：命令创建时端口所属节点为 Source，或挂载在 Source 链上的 Filter
            if (source != null) postRefresh(source);
        }
    }

    public record TransNodeState(float dx, float dy, float scaleX, float scaleY,
                                float dRotation,
                                boolean flipX, boolean flipY) {}

    public record TransformNodeCommand(Source<?> source, TransNode node, TransNodeState oldState, TransNodeState newState) implements UndoableCommand {
        @Override
        public void undo() {
            applyState(oldState);
        }

        @Override
        public void redo() {
            applyState(newState);
        }

        private void applyState(TransNodeState s) {
            node.setDx(s.dx);
            node.setDy(s.dy);
            node.setScaleX(s.scaleX);
            node.setScaleY(s.scaleY);
            node.setDRotation(s.dRotation);
            node.flipX(s.flipX);
            node.flipY(s.flipY);
            if (source != null) postRefresh(source);
        }
    }
}
