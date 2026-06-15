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
        time = toValidTime(time);
        long diff = time - getLastFrameTime();

        if (diff < 0 || diff > getLengthPerFrame()) {
            seek(time);
            Gdx.app.debug(i18n("音频解码"), hashCode() + "同步到" + time / SECOND + i18n("秒"));
        }
    }

    @Override
    public void get(long time, AudFrame frame) throws Exception {
        var a = System.currentTimeMillis();
        if (!initialized) {
            start();
        }

        long target = toValidTime(time);

        // 持续抓取音频采样直至到达目标时间位置
        Frame f = grab();
        if(false){
        int retryCount = 0;
        final int maxRetries = 50;
        while (retryCount < maxRetries) {
            f = grab();
            if (f == null) break;
            if (f.samples == null || f.samples.length == 0) break;
            if (f.timestamp+getLengthPerFrame() >= target) break;
            retryCount++;
        }
}
        if (f == null || f.samples == null || f.samples.length == 0) {
            frame.setSamples(null);
            return;
        }


        ShortBuffer sb = (ShortBuffer) f.samples[0];
        short[] out = new short[sb.remaining()];
        sb.get(out);
        frame.setSamples(out);
        var b= System.currentTimeMillis();
        System.out.println(this+";cdc:"+(b-a));
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
        return (long) (SECOND/grabber.getAudioFrameRate());
    }
}
