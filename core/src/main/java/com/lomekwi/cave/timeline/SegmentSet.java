package com.lomekwi.cave.timeline;

import com.lomekwi.cave.app.copy.Copyable;
import com.lomekwi.cave.app.selection.Selectable;

import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 当前选中的一组片段，实现 {@link Collection}{@code <Segment>}，
 */
public class SegmentSet extends AbstractCollection<Segment> implements Serializable, Selectable, Copyable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Set<Segment> segments = new LinkedHashSet<>();
    private transient boolean selected;

    @Override
    public boolean add(Segment segment) {
        return segments.add(segment);
    }

    @Override
    public boolean remove(Object o) {
        return segments.remove(o);
    }

    @Override
    public void clear() {
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
        SegmentGroup commonGroup = null;
        for (Segment seg : segments) {
            SegmentGroup g = seg.getGroup();
            if (g == null) {
                commonGroup = null;
                break;
            }
            if (commonGroup == null) {
                commonGroup = g;
            } else if (g != commonGroup) {
                commonGroup = null;
                break;
            }
        }
        if (commonGroup != null) {
            return commonGroup.copy();
        }
        SegmentSet set = new SegmentSet();
        Map<SegmentGroup, SegmentGroup> groupCopies = new HashMap<>();
        for (Segment seg : segments) {
            var dup = seg.duplicate();
            dup.setTrack(seg.getTrack());
            SegmentGroup g = seg.getGroup();
            if (g != null) {
                SegmentGroup copyG = groupCopies.get(g);
                if (copyG == null) {
                    copyG = new SegmentGroup();
                    groupCopies.put(g, copyG);
                }
                copyG.add(dup);
            }
            set.add(dup);
        }
        return set;
    }
}
