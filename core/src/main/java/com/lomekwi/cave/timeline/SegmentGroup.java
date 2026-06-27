package com.lomekwi.cave.timeline;

import java.util.HashSet;
import java.util.Set;

public class SegmentGroup {
    private final Set<Segment> segments = new HashSet<>();

    public void add(Segment segment) {
        segments.add(segment);
        segment.setGroup(this);
    }

    public void remove(Segment segment) {
        segments.remove(segment);
        segment.setGroup(null);
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public Set<Segment> getSegments() {
        return segments;
    }

    public int size() {
        return segments.size();
    }
}
