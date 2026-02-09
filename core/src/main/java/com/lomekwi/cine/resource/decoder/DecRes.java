package com.lomekwi.cine.resource.decoder;

import com.google.common.collect.Range;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.resource.Resource;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.jspecify.annotations.NonNull;

/**
 * 解码器类。不应该包含任何解码的具体逻辑，只包含状态和请求修改状态的方法。也不应该暴露内部可以被修改状态的字段，比如grabber。
 * (WIP)
 */
public abstract class DecRes<P extends Product> implements Resource {
    protected final FFmpegFrameGrabber grabber;
    protected Range<@NonNull Long> currentClipRange;
    protected P bufferedProd;
    protected boolean initialized;

    //TODO:构造方法形参改成基于输入流
    protected DecRes(String path) {
        this.grabber = new FFmpegFrameGrabber(path);
    }
    public void start() throws FrameGrabber.Exception {
        grabber.start();
        initialized = true;
    }
    public void stop() throws FrameGrabber.Exception {
        grabber.stop();
    }
    @Override
    public void close() throws FrameGrabber.Exception {
        grabber.stop();
        grabber.close();
    }
    public void setCurrentClipRange(Range<@NonNull Long> range) {
        currentClipRange = range;
    }
    public Range<@NonNull Long> getCurrentClipRange() {
        return currentClipRange;
    }
    public abstract Frame grab() throws FFmpegFrameGrabber.Exception;
    public void seek(long time) throws FFmpegFrameGrabber.Exception{
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
}
