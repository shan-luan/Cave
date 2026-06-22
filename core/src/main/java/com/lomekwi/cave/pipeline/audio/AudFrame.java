package com.lomekwi.cave.pipeline.audio;

import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.timeline.Track;

public class AudFrame extends Frame {
    private float[] samples;
    private final int sampleRate;
    private final int channels;
    private long time;

    public AudFrame(int sampleRate, int channels, Track track) {
        super(track);
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    public float[] getSamples() {
        return samples;
    }

    public AudFrame setSamples(float[] samples) {
        this.samples = samples;
        return this;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public long getTime() {
        return time;
    }

    public AudFrame setTime(long time) {
        this.time = time;
        return this;
    }

    @Override
    public void close() {
        samples = null;
    }
}
