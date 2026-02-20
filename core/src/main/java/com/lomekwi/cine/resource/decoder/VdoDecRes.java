package com.lomekwi.cine.resource.decoder;

import com.lomekwi.cine.pipeline.image.ImgProd;
import com.lomekwi.cine.resource.media.VdoRes;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

public class VdoDecRes extends DecRes<ImgProd>{
    public VdoDecRes(VdoRes source) {
        super(source);
    }
    public void setPixelFormat(int pixelFormat) {
        grabber.setPixelFormat(pixelFormat);
    }
    @Override
    public Frame grab() throws FFmpegFrameGrabber.Exception {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return grabber.grabImage();
    }
    public int getWidth() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return grabber.getImageWidth();
    }
    public int getHeight() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return grabber.getImageHeight();
    }
    public int getLengthInVideoFrames() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return grabber.getLengthInVideoFrames();
    }
    public long getLengthInTime() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return grabber.getLengthInTime();
    }
    public long getTimestamp() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return grabber.getTimestamp();
    }
    public long getLengthPerFrame() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return grabber.getLengthInTime() / grabber.getLengthInVideoFrames();
    }
}
