package com.lomekwi.cave.resource.decoder;

import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.media.VdoRes;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;

/**
 * 解码器类。
 */
public abstract class DecRes<P extends Frame> implements Resource {
    protected final FFmpegFrameGrabber grabber;
    protected long lastGrabTime = -1;
    protected P bufferedProd;
    protected boolean initialized;

    protected DecRes(VdoRes source) {
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
    public void setBufferedProduct(P product) {
        bufferedProd = product;
    }
    public P getBufferedProduct() {
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
