package com.lomekwi.cave.timeline.playback;

import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.util.Units;
public class Playhead{
    private long time= 0L;
    private  boolean isPlaying;
    private boolean isSought;
    public void setPlaying(Boolean isPlaying){
        this.isPlaying=isPlaying;
    }
    public boolean isPlaying(){
        return isPlaying;
    }
    protected void seek(long time){
        this.time=time;
        isSought=true;
    }
    public void update(){
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
