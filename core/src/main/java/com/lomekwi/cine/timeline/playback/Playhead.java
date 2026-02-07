package com.lomekwi.cine.timeline.playback;

import com.badlogic.gdx.Gdx;
import com.lomekwi.cine.util.Units;

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
        System.out.println("time:"+time+" delta:"+Gdx.graphics.getDeltaTime());
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
