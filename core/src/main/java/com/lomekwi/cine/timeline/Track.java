package com.lomekwi.cine.timeline;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.lomekwi.cine.element.FilteredSrc;

import org.jspecify.annotations.NonNull;

import java.util.Map;

public class Track {
    private final RangeMap<@NonNull Long, @NonNull FilteredSrc> elements = TreeRangeMap.create();
    private final Timeline timeline;
    private Map.Entry<Range<@NonNull Long>, @NonNull FilteredSrc> cache;

    public Track(Timeline timeline) {
        this.timeline = timeline;
    }

    public void add(FilteredSrc element, long start, long duration) {
        if(element==null){
            elements.remove(Range.closedOpen(start,start+ duration));
        }else {
            elements.put(Range.closedOpen(start, start + duration), element);
            element.setTrack(this);
        }
        cache = null;
    }

    public FilteredSrc get(long time) {
        if (cache == null || !cache.getKey().contains(time)) {
            cache = elements.getEntry(time);
        }
        if(cache==null){
            return null;
        }
        return cache.getValue();
    }

    public void remove(long time) {
        Map.Entry<Range<@NonNull Long>, FilteredSrc> entry = elements.getEntry(time);
        if (entry != null) {
            elements.remove(entry.getKey());
        }
        cache = null;
    }
    public Map.Entry<Range<@NonNull Long>, FilteredSrc> getEntry(long time){
        if (cache == null || !cache.getKey().contains(time)) {
            cache = elements.getEntry(time);
        }
        return cache;
    }
}
