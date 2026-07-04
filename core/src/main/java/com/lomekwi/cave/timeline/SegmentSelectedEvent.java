package com.lomekwi.cave.timeline;

import org.jspecify.annotations.Nullable;

public record SegmentSelectedEvent(@Nullable Segment segment, @Nullable Track track, int selectedCount) {}
