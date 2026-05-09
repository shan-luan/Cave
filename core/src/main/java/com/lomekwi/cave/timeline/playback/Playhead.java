package com.lomekwi.cave.timeline.playback;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class Playhead implements Externalizable {

    private volatile long anchor;
    private transient volatile long frozenTime = 0L;
    private transient volatile boolean isPlaying = false;
    private transient volatile boolean isSeeking = false;

    public Playhead() {
    }

    public void setPlaying(boolean playing) {
        if (playing == isPlaying) return;

        if (playing) {
            anchor = System.nanoTime() - frozenTime;
        } else {
            frozenTime = System.nanoTime() - anchor;
        }

        isPlaying = playing;
    }

    public void setSeeking(boolean seeking) {
        isSeeking = seeking;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isSeeking() {
        return isSeeking;
    }

    public void seek(long time) {
        time *= 1000;

        if (isPlaying) {
            anchor = System.nanoTime() - time;
        } else {
            frozenTime = time;
        }

        isSeeking = true;
    }

    public long getTime() {
        return getNanoTime() / 1000;
    }

    private long getNanoTime() {
        if (isPlaying) {
            return System.nanoTime() - anchor;
        } else {
            return frozenTime;
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(getTime());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException {
        long savedTime = in.readLong();
        this.frozenTime = savedTime * 1000;
    }
}
