package com.lomekwi.cave.timeline;

import com.lomekwi.cave.app.selection.SelectableSelectedEvent;
import org.jspecify.annotations.Nullable;

public record SegmentSelectedEvent(@Nullable Segment segment, @Nullable Track track, int selectedCount)
    implements SelectableSelectedEvent<Segment> {
    @Override
    public @Nullable Segment selectable() { return segment; }
}
