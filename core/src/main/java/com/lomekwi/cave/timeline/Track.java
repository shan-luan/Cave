package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.lomekwi.cave.pipeline.Product;
import com.lomekwi.cave.pipeline.Source;

import org.jspecify.annotations.NonNull;

import java.util.Map;

public class Track {
    private final RangeMap<@NonNull Long, @NonNull Source<?>> sources = TreeRangeMap.create();
    private final Timeline timeline;
    private Map.Entry<Range<@NonNull Long>, @NonNull Source<?>> cache;
    private long length;
    private boolean lengthChanged = true;

    public Track(Timeline timeline) {
        this.timeline = timeline;
    }

    public void add(Source<?> src, long start, long duration) {
        if(src==null){
            sources.remove(Range.closedOpen(start,start+ duration));
        }else {
            sources.put(Range.closedOpen(start, start + duration), src);
        }
        cache = null;
        lengthChanged = true;
    }

    public Product get(long time) {
        if (cache == null || !cache.getKey().contains(time)) {
            cache = sources.getEntry(time);
        }
        if(cache==null){
            return null;
        }
        long start=cache.getKey().lowerEndpoint();
        return cache.getValue().get(time-start);
    }

    public void remove(long time) {
        Map.Entry<Range<@NonNull Long>, Source<?>> entry = sources.getEntry(time);
        if (entry != null) {
            sources.remove(entry.getKey());
        }
        cache = null;
        lengthChanged = true;
    }
    public Map.Entry<Range<@NonNull Long>, Source<?>> getEntry(long time){
        if (cache == null || !cache.getKey().contains(time)) {
            cache = sources.getEntry(time);
        }
        return cache;
    }
    public long getLength(){
        if(lengthChanged){
            long max = 0;
            for(Range<Long> range : sources.asMapOfRanges().keySet()){
                long end = range.upperEndpoint();
                if(end > max) max = end;
            }
            length = max;
            lengthChanged = false;
        }
        return length;
    }

    public RangeMap<@NonNull Long, @NonNull Source<?>> getSources() {
        return sources;
    }
}
