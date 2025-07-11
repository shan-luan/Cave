package com.lomekwi.cine.timeline.playback;

import com.lomekwi.cine.output.OutputDispatcher;
import com.lomekwi.cine.output.Outputable;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.pipeline.Scheduler;
import com.lomekwi.cine.pipeline.SimpleScheduler;
import com.lomekwi.cine.timeline.Timeline;

import java.util.ArrayDeque;
import java.util.Queue;

public class PlayController {
    private final Timeline timeline;

    private final Playhead playhead= new Playhead();
    private final Scheduler scheduler= new SimpleScheduler();
    private final Queue<Product> collector= new ArrayDeque<>();
    private final OutputDispatcher outputDispatcher= new OutputDispatcher();


    public PlayController(Timeline timeline) {
        this.timeline = timeline;
    }
    public void addOutput(Class<? extends Product> productClass, Outputable output) {
        outputDispatcher.addOutput(productClass, output);
    }
    public void removeOutput(Class<? extends Product> productClass, Outputable output) {
        outputDispatcher.removeOutput(productClass, output);
    }
    public void update() {
        collector.clear();
        playhead.update();
        timeline.get(playhead.getTime(), collector);
        scheduler.start(collector);
        while (!collector.isEmpty()) {
            Product product = collector.poll();
            outputDispatcher.output(product);
        }
    }
    public void start(){playhead.setPlaying(true);}
    public void stop(){playhead.setPlaying(false);}
}
