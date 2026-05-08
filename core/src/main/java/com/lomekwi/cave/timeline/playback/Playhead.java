package com.lomekwi.cave.timeline.playback;

import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.util.Units;

import java.io.Serializable;

public class Playhead implements Serializable {
    private static final long serialVersionUID = 1L;
    private volatile long time= 0L;
    private transient volatile boolean isPlaying = false;
    private transient volatile boolean isSeeking = false;
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.isPlaying = false;
        this.isSeeking = false;
    }
    public void setState(PlaybackState state){
        if (state == PlaybackState.PLAYING) {
            isPlaying = true;
        } else if (state == PlaybackState.SEEKING) {
            isSeeking = true;
        }
    }
    public void clearState(PlaybackState state){
        if (state == PlaybackState.PLAYING) {
            isPlaying = false;
        } else if (state == PlaybackState.SEEKING) {
            isSeeking = false;
        }
    }
    public boolean isPlaying() {
        return isPlaying;
    }
    
    public boolean isSeeking() {
        return isSeeking;
    }
    public void seek(long time){
        this.time=time;
        this.isSeeking = true;
    }
    public void update(){
        if(isPlaying){
            time+= (long) (Gdx.graphics.getDeltaTime()*Units.SECOND);
        }
    }
    public long getTime(){
        return time;
    }
    protected void setTime(long time) {
        this.time = time;
    }
}
