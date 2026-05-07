package com.lomekwi.cave.pipeline;


public abstract class Frame implements AutoCloseable{
    public int trackIndex;
    public Frame withTrack(int trackIndex){
        this.trackIndex =trackIndex;
        return this;
    }
}
