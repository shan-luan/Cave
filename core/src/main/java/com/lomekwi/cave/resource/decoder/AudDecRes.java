package com.lomekwi.cave.resource.decoder;

import com.lomekwi.cave.resource.media.AudRes;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

public class AudDecRes extends DecRes {
    public AudDecRes(AudRes source) {
        super(source);
    }

    @Override
    public Frame grab() throws FFmpegFrameGrabber.Exception {
        return grabber.grabSamples();
    }
}
