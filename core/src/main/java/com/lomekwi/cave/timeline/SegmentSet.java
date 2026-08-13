package com.lomekwi.cave.timeline;

import com.lomekwi.cave.app.copy.Copyable;
import com.lomekwi.cave.app.selection.Selectable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class SegmentSet implements Serializable, Selectable, Copyable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Set<Segment> segments = new LinkedHashSet<>();
    private transient boolean selected;

    public void add(Segment segment) {
        segments.add(segment);
    }

    public void remove(Segment segment) {
        segments.remove(segment);
    }

    public boolean contains(Segment segment) {
        return segments.contains(segment);
    }

    public void clear() {
        segments.clear();
    }

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
