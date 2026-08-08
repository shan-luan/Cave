package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.pipeline.Modifier;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.image.AlignModifier;
import com.lomekwi.cave.pipeline.image.TransModifier;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.project.ProjectDirtyChangedEvent;
import com.lomekwi.cave.timeline.playback.RefreshRequestEvent;
import com.lomekwi.cave.ui.editpanel.detail.AlignModifierActor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

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

    public record AddSegCommand(Track track, Segment segment, long start, long duration) implements UndoableCommand {
        @Override
        public void undo() {
            var r = Range.closedOpen(start, start + duration);
            track.getTimeline().remove(track, r);
        }

        @Override
        public void redo() {
            track.getTimeline().add(track, segment, start, duration);
        }
    }

    public static class RemoveSegCommand implements UndoableCommand {
        private final Track track;
        private final Segment segment;
        private final long start;
        private final long duration;
        private final SegmentGroup group;

        public RemoveSegCommand(Track track, Segment segment, long start, long duration) {
            this(track, segment, start, duration, null);
        }

        public RemoveSegCommand(Track track, Segment segment, long start, long duration, SegmentGroup group) {
            this.track = track;
            this.segment = segment;
            this.start = start;
            this.duration = duration;
            this.group = group;
        }

        @Override
        public void undo() {
            track.getTimeline().add(track, segment, start, duration);
            if (group != null) group.add(segment);
        }

        @Override
        public void redo() {
            var r = Range.closedOpen(start, start + duration);
            track.getTimeline().remove(track, r);
            if (group != null) group.remove(segment);
        }
    }

    public record ResizeSegCommand(Track track, Segment segment, long oldStart, long oldDuration, long newStart, long newDuration) implements UndoableCommand {
        @Override
        public void undo() {
            track.getTimeline().remove(track, Range.closedOpen(newStart, newStart + newDuration));
            track.getTimeline().add(track, segment, oldStart, oldDuration);
        }

        @Override
        public void redo() {
            track.getTimeline().remove(track, Range.closedOpen(oldStart, oldStart + oldDuration));
            track.getTimeline().add(track, segment, newStart, newDuration);
        }
    }

    public record MoveSegCommand(Track fromTrack, Track toTrack, Segment segment, long oldStart, long oldDuration, long newStart, long newDuration) implements UndoableCommand {
        @Override
        public void undo() {
            toTrack.getTimeline().remove(toTrack, Range.closedOpen(newStart, newStart + newDuration));
            fromTrack.getTimeline().add(fromTrack, segment, oldStart, oldDuration);
            segment.offsetOrigin(oldStart - newStart);
        }

        @Override
        public void redo() {
            fromTrack.getTimeline().remove(fromTrack, Range.closedOpen(oldStart, oldStart + oldDuration));
            toTrack.getTimeline().add(toTrack, segment, newStart, newDuration);
            segment.offsetOrigin(newStart - oldStart);
        }
    }

    public record SplitSegCommand(Track track, Segment originalSeg, long originalStart, long originalDuration, Segment newSeg, long splitTime) implements UndoableCommand {
        @Override
        public void undo() {
            var fullRange = Range.closedOpen(originalStart, originalStart + originalDuration);
            track.getTimeline().remove(track, fullRange);
            track.getTimeline().add(track, originalSeg, originalStart, originalDuration);
        }

        @Override
        public void redo() {
            long offset = splitTime - originalStart;
            var fullRange = Range.closedOpen(originalStart, originalStart + originalDuration);
            track.getTimeline().remove(track, fullRange);
            track.getTimeline().add(track, originalSeg, originalStart, offset);
            track.getTimeline().add(track, newSeg, splitTime, originalDuration - offset);
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


    private static List modifierList(Source<?> source) {
        return source.getModifiers();
    }

    private static void postRefresh(Source<?> source) {
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

    public record AddModifierCommand(Source<?> source, Modifier<?> modifier) implements UndoableCommand {
        @Override
        public void undo() {
            modifierList(source).remove(modifier);
            postRefresh(source);
        }

        @Override
        public void redo() {
            modifierList(source).add(modifier);
            postRefresh(source);
        }
    }

    public record RemoveModifierCommand(Source<?> source, Modifier<?> modifier, int index) implements UndoableCommand {
        @Override
        public void undo() {
            modifierList(source).add(index, modifier);
            postRefresh(source);
        }

        @Override
        public void redo() {
            modifierList(source).remove(modifier);
            postRefresh(source);
        }
    }

    public record ReorderModifierCommand(Source<?> source, Modifier<?> modifier, int oldIndex, int newIndex) implements UndoableCommand {
        @Override
        public void undo() {
            modifierList(source).remove(modifier);
            modifierList(source).add(oldIndex, modifier);
            postRefresh(source);
        }

        @Override
        public void redo() {
            modifierList(source).remove(modifier);
            modifierList(source).add(newIndex, modifier);
            postRefresh(source);
        }
    }

    public record TransModifierState(float dx, float dy, float scaleX, float scaleY,
                                    float dRotation,
                                    boolean flipX, boolean flipY) {}

    public record TransformModifierCommand(TransModifier modifier, TransModifierState oldState, TransModifierState newState) implements UndoableCommand {
        @Override
        public void undo() {
            applyState(oldState);
        }

        @Override
        public void redo() {
            applyState(newState);
        }

        private void applyState(TransModifierState s) {
            modifier.dx.set(s.dx);
            modifier.dy.set(s.dy);
            modifier.scaleX.set(s.scaleX);
            modifier.scaleY.set(s.scaleY);
            modifier.dRotation.set(s.dRotation);
            modifier.flipX(s.flipX);
            modifier.flipY(s.flipY);
            modifier.invalidateDetailActor();
            postRefresh(modifier.getSource());
        }
    }

    public record AlignModifierCommand(AlignModifier modifier, AlignModifier.HAlign oldH, AlignModifier.VAlign oldV,
                                     AlignModifier.HAlign newH, AlignModifier.VAlign newV) implements UndoableCommand {
        @Override
        public void undo() {
            modifier.setAlign(oldH, oldV);
            if (modifier.getActor() instanceof AlignModifierActor aa) aa.syncFromModifier();
            postRefresh(modifier.getSource());
        }

        @Override
        public void redo() {
            modifier.setAlign(newH, newV);
            if (modifier.getActor() instanceof AlignModifierActor aa) aa.syncFromModifier();
            postRefresh(modifier.getSource());
        }
    }
}
