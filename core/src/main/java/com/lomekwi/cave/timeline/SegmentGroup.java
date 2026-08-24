package com.lomekwi.cave.timeline;

import com.lomekwi.cave.app.copy.Copyable;
import com.lomekwi.cave.app.selection.Selectable;

import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一组被同时操作（选择/拖动/分割）的片段，实现 {@link Collection}{@code <Segment>}，
 */
public class SegmentGroup extends AbstractCollection<Segment> implements Serializable, Selectable, Copyable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Set<Segment> segments = new LinkedHashSet<>();
    private transient boolean selected;

    @Override
    public boolean add(Segment segment) {
        if (segments.add(segment)) {
            segment.setGroup(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof Segment seg && segments.remove(seg)) {
            seg.setGroup(null);
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        for (Segment s : segments) s.setGroup(null);
        segments.clear();
    }

    @Override
    public boolean contains(Object o) {
        return segments.contains(o);
    }

    @Override
    public boolean isEmpty() {
        return segments.isEmpty();
    }

    @Override
    public Iterator<Segment> iterator() {
        return segments.iterator();
    }

    @Override
    public int size() {
        return segments.size();
    }

    public Set<Segment> getSegments() {
        return Collections.unmodifiableSet(segments);
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
            var dup = seg.duplicate();
            dup.setTrack(seg.getTrack());
            newGroup.add(dup);
        }
        return newGroup;
    }
}
