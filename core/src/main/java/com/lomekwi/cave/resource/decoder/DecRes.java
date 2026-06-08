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
    protected long lastGrabTime = -1;
    protected volatile boolean initialized;

    protected DecRes(MedRes source) {
        this.grabber = new FFmpegFrameGrabber(source.getPath());
    }
    public void start() throws FrameGrabber.Exception {
        grabber.start();
        initialized = true;
        Gdx.app.debug("DecRes", this +"初始化");
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

    public void seek(long time) throws FFmpegFrameGrabber.Exception{
        if (!initialized) throw new IllegalStateException("Not initialized");
        grabber.setTimestamp(time);
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
