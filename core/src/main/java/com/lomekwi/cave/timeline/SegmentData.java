package com.lomekwi.cave.timeline;

import com.lomekwi.cave.pipeline.Product;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;

public abstract class SegmentData<T extends Product> {
    private final Source<T> source;
    private Track track;
    private SegActor actor;
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
}
