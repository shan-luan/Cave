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

public class VdoDecRes extends DecRes {
    private ImgFrame bufferedFrame;

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
        bufferedFrame = new ImgFrame();
    }

    public void setBufferedFrame(ImgFrame frame) {
        bufferedFrame = frame;
    }

    public ImgFrame getBufferedFrame() {
        return bufferedFrame;
    }

    @Override
    public void close() throws Exception {
        if (bufferedFrame != null) {
            bufferedFrame.close();
        }
        super.close();
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

        long target = toValidTime(time);
        final long nextFrameTime = getTimestamp() + getLengthPerFrame();
        final long diff = target - lastGrabTime;

        // 请求时间早于上次抓取时间，需要 seek 回退
        if (diff < 0) {
            seek(target);
            Gdx.app.debug(i18n("视频解码"), hashCode() +i18n("向前跳跃")+(lastGrabTime-getTimestamp())/SECOND + i18n("秒"));
        }else if(diff > 3 * getLengthPerFrame()){// 如果请求时间间隔超过3帧间隔，则进行跳转
            seek(target);
            Gdx.app.debug(i18n("视频解码"),hashCode() +i18n("向后跳跃")+(-lastGrabTime+getTimestamp())/SECOND + i18n("秒"));

        }
        if (!((target < nextFrameTime) && bufferedFrame.getPixels() != null)) {
            Frame output = null;
            int retryCount = 0;
            final int maxRetries = 10;
            while (output == null && retryCount < maxRetries) {
                output = grab();
                retryCount++;
            }
            if (output != null) {
                bufferedFrame.setPixels((ByteBuffer) output.image[0]);
            }

        }
        // 目标时间在下一帧之前，且缓存有效，直接返回缓存
        lastGrabTime = target;
        return bufferedFrame.getPixels();
    }
}
