package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.timeline.Track;

public abstract class Frame implements AutoCloseable{
    public final Track track;
    public volatile long timestamp;

    public Frame(Track track) {
        this.track = track;
    }
    public Frame withTime(long timestamp){
        this.timestamp = timestamp;
        return this;
    }
    @Override
    public void close() {}
}
