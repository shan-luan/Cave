package com.lomekwi.cave.resource.decoder;

import static com.lomekwi.cave.util.Units.MILLISECOND;
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
    private static final long AUDIO_SEEK_THRESHOLD = 30*MILLISECOND;

    public AudDecRes(AudRes source) {
        super(source);
    }

    @Override
    public Frame grab() throws FFmpegFrameGrabber.Exception {
        return grabber.grabSamples();
    }

    @Override
    public void sync(long time) throws Exception {
        if (!initialized) {
            start();
        }
        long target = toValidTime(time);
        long diff = target - lastGrabTime;

        if (diff < 0 || diff > AUDIO_SEEK_THRESHOLD) {
            seek(target);
            Gdx.app.debug(i18n("音频解码"), hashCode() + "同步到" + target / SECOND + i18n("秒"));
        }
    }

    @Override
    public void get(long time, AudFrame frame) throws Exception {
        if (!initialized) {
            start();
        }

        long target = toValidTime(time);

        // 持续抓取音频采样直至到达目标时间位置
        Frame f = null;
        int retryCount = 0;
        final int maxRetries = 50;
        while (retryCount < maxRetries) {
            f = grab();
            if (f == null) break;
            if (f.samples == null || f.samples.length == 0) break;
            if (grabber.getTimestamp() >= target) break;
            retryCount++;
        }

        if (f == null || f.samples == null || f.samples.length == 0) {
            frame.setSamples(null);
            lastGrabTime = target;
            return;
        }

        ShortBuffer sb = (ShortBuffer) f.samples[0];
        short[] out = new short[sb.remaining()];
        sb.get(out);
        lastGrabTime = target;
        frame.setSamples(out).setTime(time);
    }

    @Override
    public void start() throws FrameGrabber.Exception {
        grabber.setSampleRate(44100);
        grabber.setAudioChannels(2);
        grabber.start();
        initialized = true;
    }
    public long getLengthPerFrame() {
        return grabber.getLengthInTime()/grabber.getLengthInAudioFrames();
    }
}
