package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.timeline.Track;

public abstract class Frame implements AutoCloseable{
    public final Track track;
    public volatile long timestamp;
    private final Source<?> source;

    public Frame(Track track) {
        this.track = track;
        this.source = null;
    }

    public Frame(Track track, Source<?> source) {
        this.track = track;
        this.source = source;
    }

    public Source<?> getSource() {
        return source;
    }

    public Frame withTime(long timestamp){
        this.timestamp = timestamp;
        return this;
    }
    @Override
    public void close() {}
}
