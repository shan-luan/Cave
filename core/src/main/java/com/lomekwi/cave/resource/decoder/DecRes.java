package com.lomekwi.cave.resource.decoder;

import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.media.MedRes;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;

/**
 * 解码器类。
 * @param <F> 产生的帧类型
 */
public abstract class DecRes<F extends Frame> implements Resource {
    protected final FFmpegFrameGrabber grabber;
    protected volatile boolean initialized;

    protected DecRes(MedRes source) {
        this.grabber = new FFmpegFrameGrabber(source.getPath());
    }
    public synchronized void start() throws FrameGrabber.Exception {
        if (initialized) return;
        configure();
        grabber.start();
        initialized = true;
        Gdx.app.debug("DecRes", this +"初始化");
    }
    protected abstract void configure();
    public void stop() throws FrameGrabber.Exception {
        if (!initialized) throw new IllegalStateException("Not initialized");
        grabber.stop();
    }
    @Override
    public void close() throws Exception {
        if (!initialized) throw new IllegalStateException("Not initialized");
        grabber.stop();
        grabber.close();
    }
    public abstract org.bytedeco.javacv.Frame grab() throws FFmpegFrameGrabber.Exception;

    /**
     * 同步到指定时间
     * @param time 局部时间
     */
    public abstract void sync(long time) throws Exception;

    /**
     * 解码指定时间的数据并更新到提供的帧对象中。
     * @param time  局部时间
     * @param frame 要更新的帧对象
     */
    public abstract void get(long time, F frame) throws Exception;

    public abstract void seek(long time) throws FFmpegFrameGrabber.Exception;
    public boolean isInitialized() {
        return initialized;
    }
    protected long toValidTime(long time) {
        return Math.min(Math.max(0,time), getLengthInTime());
    }
    protected boolean isTimeLegal(long time){
        return toValidTime(time)==time;
    }
    public long getLengthInTime() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return Math.max(grabber.getLengthInTime(),0);
    }
    public long getTimestamp() {
        if (!initialized) throw new IllegalStateException("Not initialized");
        return grabber.getTimestamp();
    }
    public abstract long getLengthPerFrame();
    public long getLastFrameTime() {
        return getTimestamp()-getTimestamp()%getLengthPerFrame();
    }
}
