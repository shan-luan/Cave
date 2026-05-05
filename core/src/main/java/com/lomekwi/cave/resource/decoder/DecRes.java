package com.lomekwi.cave.resource.decoder;

import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.media.MedRes;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;

/**
 * 解码器类。
 */
public abstract class DecRes<F extends Frame> implements Resource {
    protected final FFmpegFrameGrabber grabber;
    protected long lastGrabTime = -1;
    protected F bufferedProd;
    protected boolean initialized;

    protected DecRes(MedRes source) {
        this.grabber = new FFmpegFrameGrabber(source.getPath());
    }
    public void start() throws FrameGrabber.Exception {
        grabber.start();
        initialized = true;
    }
    public void stop() throws FrameGrabber.Exception {
        if (!initialized) throw new IllegalStateException("Not initialized");
        grabber.stop();
    }
    @Override
    public void close() throws Exception {
        if (!initialized) throw new IllegalStateException("Not initialized");
        grabber.stop();
        grabber.close();
        bufferedProd.close();
    }
    public abstract org.bytedeco.javacv.Frame grab() throws FFmpegFrameGrabber.Exception;
    public void seek(long time) throws FFmpegFrameGrabber.Exception{
        if (!initialized) throw new IllegalStateException("Not initialized");
        grabber.setTimestamp(time);
    }
    public void setBufferedProduct(F product) {
        bufferedProd = product;
    }
    public F getBufferedProduct() {
        return bufferedProd;
    }
    public boolean isInitialized() {
        return initialized;
    }

    public long getLastGrabTime() {
        return lastGrabTime;
    }

    public void setLastGrabTime(long lastGrabTime) {
        this.lastGrabTime = lastGrabTime;
    }
    protected long toValidTime(long time) {
        return Math.min(Math.max(0,time), getLengthInTime());
    }
    public long getLengthInTime() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return grabber.getLengthInTime();
    }
}
