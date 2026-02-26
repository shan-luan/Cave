package com.lomekwi.cave.resource.decoder;

import com.google.common.collect.Range;
import com.lomekwi.cave.pipeline.Product;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.media.VdoRes;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.jspecify.annotations.NonNull;

import java.io.InputStream;

/**
 * 解码器类。不应该包含任何解码的具体逻辑，只包含状态和请求修改状态的方法。也不应该暴露内部可以被修改状态的字段，比如grabber。
 */
public abstract class DecRes<P extends Product> implements Resource {
    protected final FFmpegFrameGrabber grabber;
    protected long lastGrabTime = -1;
    protected P bufferedProd;
    protected boolean initialized;

    //TODO:构造方法形参改成基于输入流
    protected DecRes(VdoRes source) {
        this.grabber = new FFmpegFrameGrabber(source.getInputStream());
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
    public void close() throws FrameGrabber.Exception {
        if (!initialized) throw new IllegalStateException("Not initialized");
        grabber.stop();
        grabber.close();
    }
    public abstract Frame grab() throws FFmpegFrameGrabber.Exception;
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
}
