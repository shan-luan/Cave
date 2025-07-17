package com.lomekwi.cine.timeline.playback;

import com.badlogic.gdx.Gdx;

public class Playhead{
    private long time= 0L;
    private  boolean isPlaying;
    private boolean isSought;
    protected void setPlaying(Boolean isPlaying){
        this.isPlaying=isPlaying;
    }
    protected void seek(long time){
        this.time=time;
        isSought=true;
    }
    protected void update(){
        if(isPlaying){
            time+= (long) (Gdx.graphics.getDeltaTime()*1e6);
        }
    }
    public long getTime(){
        return time;
    }

    public boolean isSought() {
        return isSought;
    }
    protected void resetSought(){
        isSought=false;
    }
}
