package com.lomekwi.cave.timeline;

import com.lomekwi.cave.app.selection.SelectableSelectedEvent;
import org.jspecify.annotations.Nullable;

public record SegmentSetSelectedEvent(SegmentSet set, int selectedCount)
    implements SelectableSelectedEvent<SegmentSet> {
    @Override
    public @Nullable SegmentSet selectable() { return set; }
}
