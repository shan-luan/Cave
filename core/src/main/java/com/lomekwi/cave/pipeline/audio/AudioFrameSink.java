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
    private static volatile Future<?> currentMixerFuture;

    @Subscribe
    public void sink(AudFrame frame) {
        frame.track.getWorker().getSinkPhaser().register();
        afm.submit(frame);
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
        private final LinkedBlockingQueue<AudFrame> frames=new LinkedBlockingQueue<>();
        private final float[] output=new float[AudDecRes.FRAME_SIZE];
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                Arrays.fill(output,0f);
                AudFrame f;
                try {
                    f = frames.take();
                } catch (InterruptedException e) {
                    break;
                }
                do {
                    int j=0;
                    for(var sample : f.getSamples()){
                        output[j]+=sample;
                        j++;
                    }
                    f.track.getWorker().getSinkPhaser().arriveAndDeregister();
                } while ((f = frames.poll()) != null);
                clamp(output);
                App.audioOut.getAudioDevice().writeSamples(output,0,AudDecRes.FRAME_SIZE);
            }
        }
        private void clamp(float[] samples){
            for(int i=0;i<samples.length;i++){
                samples[i]=Math.max(-1.0f,Math.min(1.0f,samples[i]));
            }
        }
        public void submit(AudFrame f){
            frames.add(f);
        }
    }
}
