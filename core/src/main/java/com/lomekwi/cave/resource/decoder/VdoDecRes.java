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
    private ByteBuffer bufferedPixels;

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
        return Math.round(SECOND / grabber.getFrameRate());
    }

    @Override
    public void start() throws FrameGrabber.Exception {
        grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        grabber.setAudioChannels(0);
        super.start();
        bufferedPixels = null;
    }

    public void setBufferedPixels(ByteBuffer pixels) {
        bufferedPixels = pixels;
    }

    public ByteBuffer getBufferedPixels() {
        return bufferedPixels;
    }

    @Override
    public void close() throws Exception {
        super.close();
    }

    @Override
    public void sync(long time) throws Exception {
        if (!initialized) {
            start();
        }

        time = toValidTime(time);
        final long diff = time - lastGrabTime;

        if (diff < 0 || diff > 3 * getLengthPerFrame()) {
            // 请求时间早于上次抓取时间或时间间隔超过3帧，需要 seek
            seek(time);
            Gdx.app.debug(i18n("视频解码"), hashCode() + "同步到" + time / SECOND + i18n("秒"));
            bufferedPixels = null;
        }
    }

    /**
     * 解码指定时间戳的视频帧，并将像素数据更新到提供的帧对象中。
     * 内部会根据时间戳判断是否使用缓存、跳转或抓取新帧。
     *
     * @param time  目标时间
     * @param frame 要更新的帧对象
     * @throws Exception 解码过程中的异常
     */
    @Override
    public void get(long time, ImgFrame frame) throws Exception {
        if (!initialized) {
            start();
        }

        time = toValidTime(time);
        final long nextFrameTime = getTimestamp() + getLengthPerFrame();

        if (!((time < nextFrameTime) && bufferedPixels != null)) {
            Frame output = null;
            int retryCount = 0;
            final int maxRetries = 10;
            while (output == null && retryCount < maxRetries) {
                output = grab();
                retryCount++;
            }
            if (output != null) {
                bufferedPixels = (ByteBuffer) output.image[0];
            }
        }

        // 目标时间在下一帧之前，且缓存有效，直接返回缓存
        lastGrabTime = time;
        frame.setPixels(bufferedPixels);
    }
}
