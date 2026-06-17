package com.lomekwi.cave.pipeline.audio;

import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.app.AppAudioOut;
import com.lomekwi.cave.project.ProjectBackgroundedEvent;
import com.lomekwi.cave.project.ProjectFrontedEvent;

import java.util.concurrent.Future;

public class AudioFrameSink {
    private final AudioFrameMixer afm = new AudioFrameMixer();
    private static volatile Future<?> currentMixerFuture;

    @Subscribe
    public void sink(AudFrame frame) {
        AppAudioOut.getInstance().getAudioDevice().writeSamples(frame.getSamples(),0,frame.getSamples().length);
    }

    @Subscribe
    public void onProjectFronted(ProjectFrontedEvent event) {
        synchronized (AudioFrameSink.class) {
            if (currentMixerFuture != null && !currentMixerFuture.isDone()) {
                currentMixerFuture.cancel(true);
            }
            currentMixerFuture = App.audioExecutor.submit(afm);
        }
    }

    @Subscribe
    public void onProjectBackgrounded(ProjectBackgroundedEvent event) {
        if (currentMixerFuture != null && !currentMixerFuture.isDone()) {
            currentMixerFuture.cancel(true);
        }
    }

    protected class AudioFrameMixer implements Runnable {
        @Override
        public void run() {
            //TODO
        }
    }
}
