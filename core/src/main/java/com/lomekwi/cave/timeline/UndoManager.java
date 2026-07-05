package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.project.ProjectDirtyChangedEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.Deque;

@NullMarked
public class UndoManager {
    private static final int MAX_UNDO = 100;
    private final Deque<UndoableCommand> undoStack = new ArrayDeque<>();
    private final Deque<UndoableCommand> redoStack = new ArrayDeque<>();
    private transient Project project;

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

    public record RemoveSegCommand(Track track, Segment segment, long start, long duration) implements UndoableCommand {
        @Override
        public void undo() {
            track.getTimeline().add(track, segment, start, duration);
        }

        @Override
        public void redo() {
            var r = Range.closedOpen(start, start + duration);
            track.getTimeline().remove(track, r);
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
}
