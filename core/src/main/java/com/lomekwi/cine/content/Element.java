package com.lomekwi.cine.content;

import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.timeline.playback.Playhead;

public abstract class Element implements Product {
    protected final long duration;
    protected final long start;
    protected final Playhead playhead;

    protected Element(long duration,long start, Playhead playhead){
        this.duration = duration;
        this.start = start;
        this.playhead = playhead;
    }
    public long getStart() {
        return start;
    }

    public long getEnd() {
        return start+ duration;
    }
    public abstract void dispose();

    public Playhead getPlayhead() {
        return playhead;
    }
}
