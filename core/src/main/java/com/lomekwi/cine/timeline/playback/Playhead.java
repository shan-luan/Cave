package com.lomekwi.cine.timeline.playback;

import com.badlogic.gdx.Gdx;
import com.lomekwi.cine.util.Units;
//WIP,unwork
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
            time+= (long) (Gdx.graphics.getDeltaTime()*Units.SECOND);
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
