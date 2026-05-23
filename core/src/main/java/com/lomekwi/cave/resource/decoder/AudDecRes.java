package com.lomekwi.cave.resource.decoder;

import com.lomekwi.cave.pipeline.audio.AudFrame;
import com.lomekwi.cave.resource.media.AudRes;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.nio.ShortBuffer;

public class AudDecRes extends DecRes<AudFrame> {
    public AudDecRes(AudRes source) {
        super(source);
    }

    @Override
    public Frame grab() throws FFmpegFrameGrabber.Exception {
        return grabber.grabSamples();
    }

    @Override
    public void get(long time, AudFrame frame) throws Exception {
        Frame f = grab();
        if (f == null || f.samples == null || f.samples.length == 0) {
            frame.setSamples(null);
            return;
        }
        ShortBuffer sb = (ShortBuffer) f.samples[0];
        short[] out = new short[sb.remaining()];
        sb.get(out);
        frame.setSamples(out).setTime(time);
    }
    @Override
    public void start() throws FrameGrabber.Exception {
        grabber.setSampleRate(44100);
        grabber.setAudioChannels(2);
        grabber.start();
        initialized = true;
    }
}
