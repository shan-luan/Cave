package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.pipeline.Product;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;

import java.util.AbstractMap;

public abstract class SegmentData<T extends Product> {
    private final Source<T> source;
    private Track track;
    private SegActor actor;
    private Range<Long> range;
    private AbstractMap.SimpleImmutableEntry<Range<Long>,SegmentData<?>> entry;
    public long origin;
    protected SegmentData(Source<T> source,long origin) {
        this.source = source;
        this.origin = origin;
    }
    protected void setActor(SegActor actor) {
        this.actor = actor;
    }
    protected void setTrack(Track track) {
        this.track = track;
    }

    public T get(long time) {
        return source.get(toLocalTime(time));
    }
    public SegActor getActor() {
        return actor;
    }
    public long toLocalTime(long time) {
        return time - origin;
    }
    public Track getTrack(){
        return track;
    }

    public Range<Long> getRange() {
        return range;
    }
    public AbstractMap.Entry<Range<Long>,SegmentData<?>> getEntry() {
        return entry;
    }
    protected void setRange(Range<Long> range) {
        if(range.equals(this.range))return;
        this.range = range;
        entry = new AbstractMap.SimpleImmutableEntry<>(range,this);
    }
}
