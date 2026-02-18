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
        return grabber.grabImage();
    }
    public int getWidth() {
        return grabber.getImageWidth();
    }
    public int getHeight() {
        return grabber.getImageHeight();
    }
    public int getLengthInVideoFrames() {
        return grabber.getLengthInVideoFrames();
    }
    public long getLengthInTime() {
        return grabber.getLengthInTime();
    }
    public long getTimestamp() {
        return grabber.getTimestamp();
    }
    public long getLengthPerFrame() {
        return grabber.getLengthInTime() / grabber.getLengthInVideoFrames();
    }
}
