package com.lomekwi.cave.pipeline;


public abstract class Frame implements AutoCloseable{
    public volatile int trackIndex;
    public Frame withTrack(int trackIndex){
        this.trackIndex =trackIndex;
        return this;
    }
}
