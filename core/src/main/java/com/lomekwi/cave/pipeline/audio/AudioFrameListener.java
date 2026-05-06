package com.lomekwi.cave.pipeline.audio;

import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.app.AppAudioOut;

public class AudioFrameListener {

    @Subscribe
    public void onAudFrame(AudFrame frame) {
        System.out.println("p"+System.nanoTime());
        AppAudioOut.getInstance().getAudioDevice().writeSamples(frame.getSamples(), 0, frame.getSamples().length);
        System.out.println("P"+System.nanoTime());
    }
}
