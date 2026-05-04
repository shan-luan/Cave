package com.lomekwi.cave.timeline.playback;

import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.util.Units;

import java.io.Serializable;
import java.util.EnumSet;

public class Playhead implements Serializable {
    private static final long serialVersionUID = 1L;
    private long time= 0L;
    private transient EnumSet<PlaybackState> states = EnumSet.noneOf(PlaybackState.class);
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.states = EnumSet.noneOf(PlaybackState.class);
    }
    public void setState(PlaybackState state){
        if (state == PlaybackState.PLAYING) {
            states.add(PlaybackState.PLAYING);
        } else if (state == PlaybackState.SEEKING) {
            states.add(PlaybackState.SEEKING);
        }
    }
    public void clearState(PlaybackState state){
        states.remove(state);
    }
    public EnumSet<PlaybackState> getStates(){
        return states;
    }
    public void seek(long time){
        this.time=time;
        this.states.add(PlaybackState.SEEKING);
    }
    public void update(){
        if(states.contains(PlaybackState.PLAYING) && !states.contains(PlaybackState.SEEKING)){
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
