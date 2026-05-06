package com.lomekwi.cave.pipeline.audio;

import com.lomekwi.cave.pipeline.Frame;

public class AudFrame extends Frame {
    private short[] samples;
    private int sampleRate;
    private int channels;
    private long time;

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

    public AudFrame setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        return this;
    }

    public int getChannels() {
        return channels;
    }

    public AudFrame setChannels(int channels) {
        this.channels = channels;
        return this;
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
