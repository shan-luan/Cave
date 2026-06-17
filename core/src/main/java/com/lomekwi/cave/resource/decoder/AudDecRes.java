package com.lomekwi.cave.resource.decoder;

import static com.lomekwi.cave.util.Units.SECOND;
import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.pipeline.audio.AudFrame;
import com.lomekwi.cave.resource.media.AudRes;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.nio.ShortBuffer;

public class AudDecRes extends DecRes<AudFrame> {

    private static final int FRAME_SIZE = 1024;
    private final short[] sampleBuf = new short[FRAME_SIZE * 8];
    private int bufLen;

    public AudDecRes(AudRes source) {
        super(source);
    }

    @Override
    public Frame grab() throws FFmpegFrameGrabber.Exception {
        return grabber.grabSamples();
    }

    @Override
    public void seek(long time) throws FFmpegFrameGrabber.Exception {
        seek(time, 0);        //由于鬼知道什么的原因，有时候即使传参的时间合法也会在底层触发一个非法参数的跳跃（比如目标负数时间），所以加个保险。
    }

    private void seek(long time, int retry) throws FFmpegFrameGrabber.Exception {
        bufLen = 0;
        try {
            grabber.setAudioTimestamp(time);
        } catch (FFmpegFrameGrabber.Exception e) {
            if (retry >= 10) {
                throw e;
            }
            seek(time + 1000, retry + 1);
        }
    }


    @Override
    public void sync(long time) throws Exception {
        if (!initialized) {
            start();
        }
        time = toValidTime(time);
        System.out.println(time);
        seek(time);
    }

    @Override
    public void get(long time, AudFrame frame) throws Exception {
        if (!initialized) {
            start();
        }

        if(!isTimeLegal(time)){
            frame.setSamples(null);
            return;
        }

        short[] output = new short[FRAME_SIZE];
        int written = 0;

        // 先使用缓冲区中遗留的采样点，没有则轻量同步到目标时间
        if (bufLen > 0) {
            int toCopy = Math.min(bufLen, FRAME_SIZE);
            System.arraycopy(sampleBuf, 0, output, 0, toCopy);
            written = toCopy;
            if (toCopy < bufLen) {
                System.arraycopy(sampleBuf, toCopy, sampleBuf, 0, bufLen - toCopy);
                bufLen -= toCopy;
            } else {
                bufLen = 0;
            }
        } else {
            for (int i = 0; i < 50; i++) {
                Frame f = grab();
                if (f == null || f.samples == null || f.samples.length == 0) {
                    frame.setSamples(null);
                    return;
                }
                if (f.timestamp + getLengthPerFrame() >= time) {
                    ShortBuffer sb = (ShortBuffer) f.samples[0];
                    int remaining = sb.remaining();
                    if (remaining <= FRAME_SIZE) {
                        sb.get(output, 0, remaining);
                        written = remaining;
                    } else {
                        sb.get(output, 0, FRAME_SIZE);
                        written = FRAME_SIZE;
                        int excess = remaining - FRAME_SIZE;
                        sb.get(sampleBuf, 0, excess);
                        bufLen = excess;
                    }
                    break;
                }
            }
        }

        // 持续抓取原始帧直至凑够 FRAME_SIZE 个采样点
        while (written < FRAME_SIZE) {
            Frame f = grab();
            if (f == null || f.samples == null || f.samples.length == 0) {
                if (written == 0) {
                    frame.setSamples(null);
                    return;
                }
                // 文件末尾不足部分以静音填充（output 已初始化为 0）
                break;
            }

            ShortBuffer sb = (ShortBuffer) f.samples[0];
            int remaining = sb.remaining();
            int need = FRAME_SIZE - written;

            if (remaining <= need) {
                sb.get(output, written, remaining);
                written += remaining;
            } else {
                sb.get(output, written, need);
                written = FRAME_SIZE;
                int excess = remaining - need;
                sb.get(sampleBuf, 0, excess);
                bufLen = excess;
            }
        }

        frame.setSamples(output);
    }

    @Override
    public void start() throws FrameGrabber.Exception {
        grabber.setSampleRate(44100);
        grabber.setAudioChannels(2);
        grabber.start();
        initialized = true;
    }

    @Override
    public long getLengthPerFrame() {
        return FRAME_SIZE / 2 * SECOND / 44100;
    }
}
