package com.lomekwi.cave.resource.decoder;

import static com.lomekwi.cave.util.Units.SECOND;
import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.pipeline.image.ImgFrame;
import com.lomekwi.cave.resource.media.VdoRes;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.nio.ByteBuffer;

public class VdoDecRes extends DecRes<ImgFrame> {

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
        setBufferedProduct(new ImgFrame());
    }

    /**
     * 解码指定时间戳的视频帧，并返回像素数据（ByteBuffer）。
     * 内部会根据时间戳判断是否使用缓存、跳转或抓取新帧。
     *
     * @param time 目标时间
     * @return 解码后的像素数据 ByteBuffer
     * @throws Exception 解码过程中的异常
     */
    public ByteBuffer decodeFrameAtTime(long time) throws Exception {
        if (!initialized) {
            start();
        }

        final long target = Math.min(Math.max(0,time), getLengthInTime());
        final long nextFrameTime = getTimestamp() + getLengthPerFrame();
        final long diff = time - lastGrabTime;

        // 请求时间早于上次抓取时间，需要 seek 回退
        if (diff < 0) {
            seek(target);
            Gdx.app.debug(i18n("视频解码"), hashCode() +i18n("向前跳跃")+(lastGrabTime-getTimestamp())/SECOND + i18n("秒"));
        }else if(diff > 2 * getLengthPerFrame()){// 如果请求时间间隔超过两帧间隔，则进行跳转
            seek(target);
            Gdx.app.debug(i18n("视频解码"),hashCode() +i18n("向后跳跃")+(-lastGrabTime+getTimestamp())/SECOND + i18n("秒"));
            time=getTimestamp();//防止跳转误差造成的反复跳转
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
