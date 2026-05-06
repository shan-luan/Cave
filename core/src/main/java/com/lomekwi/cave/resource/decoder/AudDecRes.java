package com.lomekwi.cave.resource.decoder;

import com.lomekwi.cave.resource.media.AudRes;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.nio.ShortBuffer;

public class AudDecRes extends DecRes {
    public AudDecRes(AudRes source) {
        super(source);
    }

    @Override
    public Frame grab() throws FFmpegFrameGrabber.Exception {
        return grabber.grabSamples();
    }

    public short[] decodeFrameAtTime(long time) throws Exception {
        Frame f = grab();
        if (f == null || f.samples == null || f.samples.length == 0) return null;
        ShortBuffer sb = (ShortBuffer) f.samples[0];
        short[] out = new short[sb.remaining()];
        sb.get(out);
        return out;
    }
}
