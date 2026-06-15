package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.timeline.Track;

public abstract class Frame implements AutoCloseable{
    public volatile Track track;
    public volatile long timestamp;
    public Frame withTrack(Track track){
        this.track = track;
        return this;
    }
    public Frame withTime(long timestamp){
        this.timestamp = timestamp;
        return this;
    }
    @Override
    public void close() {}
}
