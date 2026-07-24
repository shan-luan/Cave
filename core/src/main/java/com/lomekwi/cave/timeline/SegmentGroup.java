package com.lomekwi.cave.timeline;

import com.lomekwi.cave.app.copy.Copyable;
import com.lomekwi.cave.app.selection.Selectable;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class SegmentGroup implements Serializable, Selectable, Copyable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Set<Segment> segments = new LinkedHashSet<>();
    private transient boolean selected;

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

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
        for (Segment seg : segments) {
            seg.setSelected(selected);
        }
    }

    @Override
    public Copyable copy() {
        SegmentGroup newGroup = new SegmentGroup();
        for (Segment seg : segments) {
            newGroup.add((Segment) seg.duplicate());
        }
        return newGroup;
    }
}