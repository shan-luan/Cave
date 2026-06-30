package com.lomekwi.cave.timeline;

import org.jspecify.annotations.Nullable;

public class SegmentSelectedEvent {
    private final @Nullable Segment segment;
    private final @Nullable Track track;
    private final int selectedCount;

    public SegmentSelectedEvent(@Nullable Segment segment, @Nullable Track track, int selectedCount) {
        this.segment = segment;
        this.track = track;
        this.selectedCount = selectedCount;
    }

    public @Nullable Segment getSegment() {
        return segment;
    }

    public @Nullable Track getTrack() {
        return track;
    }

    public int getSelectedCount() {
        return selectedCount;
    }
}
