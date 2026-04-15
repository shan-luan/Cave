package com.lomekwi.cave.timeline;

import com.google.common.collect.Range;
import com.lomekwi.cave.pipeline.Product;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;

import java.util.AbstractMap;

public abstract class Segment {
    private final Source source;
    private Track track;
    private SegActor actor;
    private Range<Long> range;
    private AbstractMap.SimpleImmutableEntry<Range<Long>, Segment> entry;
    /**
     * 源的0秒在时间轴中的位置
     */
    public long origin;
    protected Segment(Source source) {
        this.source = source;
    }
    protected void setActor(SegActor actor) {
        this.actor = actor;
    }
    protected void setTrack(Track track) {
        this.track = track;
    }

    /**
     * @param time 绝对时间
     */
    public Product get(long time,Track track) {
        return source.get(toLocalTime(time), track);
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
    public AbstractMap.Entry<Range<Long>, Segment> getEntry() {
        return entry;
    }
    protected void setRange(Range<Long> range) {
        if(range.equals(this.range))return;
        this.range = range;
        entry = new AbstractMap.SimpleImmutableEntry<>(range,this);
    }
}
