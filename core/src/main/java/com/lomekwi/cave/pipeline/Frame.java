package com.lomekwi.cave.pipeline;


public abstract class Frame implements AutoCloseable{
    public volatile int trackIndex;
    public volatile long timestamp;
    public Frame withTrack(int trackIndex){
        this.trackIndex =trackIndex;
        return this;
    }
    public Frame withTime(long timestamp){
        this.timestamp = timestamp;
        return this;
    }
    @Override
    public void close() {}
}
