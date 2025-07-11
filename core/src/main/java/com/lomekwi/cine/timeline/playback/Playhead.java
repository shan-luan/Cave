package com.lomekwi.cine.timeline.playback;

import com.badlogic.gdx.Gdx;

public class Playhead{
    long time;
    boolean isPlaying;
    public void setPlaying(Boolean isPlaying){
        this.isPlaying=isPlaying;
    }
    public void seek(long time){
        this.time=time;
    }
    public void update(){
        if(isPlaying){
            time+= (long) (Gdx.graphics.getDeltaTime()*1e6);
        }
    }
    public long getTime(){
        return time;
    }
}
