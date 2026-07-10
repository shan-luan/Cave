package com.lomekwi.cave.timeline;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class SegmentGroup implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
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
