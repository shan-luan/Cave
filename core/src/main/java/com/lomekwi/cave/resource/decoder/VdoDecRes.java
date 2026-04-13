package com.lomekwi.cave.resource.decoder;

import com.lomekwi.cave.pipeline.image.ImgProd;
import com.lomekwi.cave.resource.media.VdoRes;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.nio.ByteBuffer;

public class VdoDecRes extends DecRes<ImgProd> {

    public VdoDecRes(VdoRes source) {
        super(source);
    }

    protected void setPixelFormat(int pixelFormat) {
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

    @Override
    public void start() throws FrameGrabber.Exception {
        grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        super.start();
        setBufferedProduct(new ImgProd());
    }

    /**
     * 解码指定时间戳的视频帧，并返回像素数据（ByteBuffer）。
     * 内部会根据时间戳判断是否使用缓存、跳转或抓取新帧。
     *
     * @param time 目标时间戳（与 getLengthInTime() 单位一致）
     * @return 解码后的像素数据 ByteBuffer
     * @throws Exception 解码过程中的异常
     */
    public ByteBuffer decodeFrameAtTime(long time) throws Exception {
        if (!initialized) {
            start();
        }

        final long target = Math.min(time, getLengthInTime());
        final long nextFrameTime = getTimestamp() + getLengthPerFrame();
        final long diff = time - lastGrabTime;

        // 请求时间早于上次抓取时间，需要 seek 回退
        if (diff < 0) {
            seek(target);
            if (getTimestamp() > target) {
                System.err.println("over jumped, Delta:" + (getTimestamp() - target));
            }
        } else if ((target < nextFrameTime) && bufferedProd.getPixels() != null) {
            // 目标时间在下一帧之前，且缓存有效，直接返回缓存
            lastGrabTime = time;
            return bufferedProd.getPixels();
        }

        // 需要抓取新帧
        Frame output = grab();
        if (output != null) {
            bufferedProd.setPixels((ByteBuffer) output.image[0]);
        }
        lastGrabTime = time;
        return bufferedProd.getPixels();
    }
}
