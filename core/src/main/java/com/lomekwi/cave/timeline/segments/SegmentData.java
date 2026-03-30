package com.lomekwi.cave.timeline.segments;

import com.lomekwi.cave.pipeline.Product;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;

public abstract class SegmentData<T extends Product> {
    private final Source<T> source;
    private final SegActor actor;
    public long origin;
    protected SegmentData(Source<T> source, SegActor actor, long origin) {
        this.source = source;
        this.actor = actor;
        this.origin = origin;
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
}
