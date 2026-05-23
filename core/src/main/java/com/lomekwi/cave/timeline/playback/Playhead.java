package com.lomekwi.cave.timeline.playback;

import com.google.common.eventbus.EventBus;

import org.jspecify.annotations.NonNull;

public class Playhead {

    private volatile long anchor;
    private volatile long frozenTime = 0L;
    private volatile boolean isPlaying = false;
    private final transient @NonNull EventBus projEventBus;

    public Playhead(@NonNull EventBus projEventBus) {
        this.projEventBus = projEventBus;
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

    public boolean isPlaying() {
        return isPlaying;
    }

    public void seek(long time) {
        time *= 1000;

        if (isPlaying) {
            anchor = System.nanoTime() - time;
        } else {
            frozenTime = time;
        }

        projEventBus.post(SeekEvent.INSTANCE);
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
}
