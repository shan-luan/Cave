package com.lomekwi.cave.pipeline.audio;

import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.project.ProjectBackgroundedEvent;
import com.lomekwi.cave.project.ProjectFrontedEvent;
import com.lomekwi.cave.resource.decoder.AudDecRes;

import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class AudioFrameSink {
    private volatile AudioFrameMixer afm = new AudioFrameMixer();
    private Future<?> currentFuture;

    @Subscribe
    public void sink(AudFrame frame) {
        frame.track.getWorker().getSinkPhaser().register();
        afm.submit(frame);
    }

    @Subscribe
    public void onProjectFronted(ProjectFrontedEvent event) {
        stopMixer();
        afm = new AudioFrameMixer();
        currentFuture = App.workerExecutor.submit(afm);
    }

    @Subscribe
    public void onProjectBackgrounded(ProjectBackgroundedEvent event) {
        stopMixer();
    }

    private void stopMixer() {
        Future<?> f = currentFuture;
        currentFuture = null;
        if (f == null) return;
        afm.stop();
        try {
            f.get(200, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            f.cancel(true);
        }
    }

    protected static class AudioFrameMixer implements Runnable {
        private static final long POLL_TIMEOUT_MILLIS = 20;

        private final LinkedBlockingQueue<AudFrame> frames = new LinkedBlockingQueue<>();
        private final float[] output = new float[AudDecRes.FRAME_SIZE];
        private volatile boolean stopped;

        public void stop() {
            stopped = true;
        }

        @Override
        public void run() {
            while (!stopped) {
                Arrays.fill(output, 0f);
                AudFrame f;
                try {
                    f = frames.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    break;
                }
                if (f == null) continue;
                do {
                    int j = 0;
                    for (var sample : f.getSamples()) {
                        output[j] += sample;
                        j++;
                    }
                    f.track.getWorker().getSinkPhaser().arriveAndDeregister();
                    if (stopped || Thread.currentThread().isInterrupted()) break;
                } while ((f = frames.poll()) != null);
                if (stopped || Thread.currentThread().isInterrupted()) break;
                clamp(output);
                App.audioOut.writeSamples(output);
            }
        }

        private void clamp(float[] samples) {
            for (int i = 0; i < samples.length; i++) {
                samples[i] = Math.max(-1.0f, Math.min(1.0f, samples[i]));
            }
        }

        public void submit(AudFrame f) {
            frames.add(f);
        }
    }
}
