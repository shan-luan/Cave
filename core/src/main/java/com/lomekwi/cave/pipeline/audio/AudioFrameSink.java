package com.lomekwi.cave.pipeline.audio;

import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.project.ProjectBackgroundedEvent;
import com.lomekwi.cave.project.ProjectFrontedEvent;
import com.lomekwi.cave.resource.decoder.AudDecRes;

import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

public class AudioFrameSink {
    private final AudioFrameMixer afm = new AudioFrameMixer();
    private Future<?> currentFuture;

    @Subscribe
    public void sink(AudFrame frame) {
        frame.track.getWorker().getSinkPhaser().register();
        afm.submit(frame);
    }

    @Subscribe
    public void onProjectFronted(ProjectFrontedEvent event) {
        if (currentFuture != null && !currentFuture.isDone()) {
            currentFuture.cancel(true);
        }
        afm.clear();
        currentFuture = App.workerExecutor.submit(afm);
    }

    @Subscribe
    public void onProjectBackgrounded(ProjectBackgroundedEvent event) {
        if (currentFuture != null && !currentFuture.isDone()) {
            currentFuture.cancel(true);
        }
    }

    protected class AudioFrameMixer implements Runnable {
        private final LinkedBlockingQueue<AudFrame> frames = new LinkedBlockingQueue<>();
        private final float[] output = new float[AudDecRes.FRAME_SIZE];

        public void clear() {
            frames.clear();
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                Arrays.fill(output, 0f);
                AudFrame f;
                try {
                    f = frames.take();
                } catch (InterruptedException e) {
                    break;
                }
                do {
                    int j = 0;
                    for (var sample : f.getSamples()) {
                        output[j] += sample;
                        j++;
                    }
                    f.track.getWorker().getSinkPhaser().arriveAndDeregister();
                    if (Thread.currentThread().isInterrupted()) break;
                } while ((f = frames.poll()) != null);
                if (Thread.currentThread().isInterrupted()) break;
                clamp(output);
                App.audioOut.getAudioDevice().writeSamples(output, 0, AudDecRes.FRAME_SIZE);
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
