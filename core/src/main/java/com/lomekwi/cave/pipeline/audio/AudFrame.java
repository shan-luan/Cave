package com.lomekwi.cave.pipeline.audio;

import com.lomekwi.cave.pipeline.Frame;

public class AudFrame extends Frame {
    private short[] samples;
    private final int sampleRate;
    private final int channels;
    private long time;

    public AudFrame(int sampleRate, int channels) {
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    public short[] getSamples() {
        return samples;
    }

    public AudFrame setSamples(short[] samples) {
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
