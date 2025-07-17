package com.lomekwi.cine.timeline.playback;

import com.lomekwi.cine.content.Element;
import com.lomekwi.cine.output.OutputDispatcher;
import com.lomekwi.cine.output.Outputter;
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
    public void addOutput(Class<? extends Product> productClass, Outputter output) {
        outputDispatcher.addOutput(productClass, output);
    }
    public void removeOutput(Class<? extends Product> productClass, Outputter output) {
        outputDispatcher.removeOutput(productClass, output);
    }
    //TODO:如果有性能问题就全改成并行流
    public void update() {
        collector.clear();
        playhead.update();
        timeline.get(playhead.getTime(), collector);
        scheduler.start(collector);
        outputDispatcher.resetAll();
        while (!collector.isEmpty()) {
            Product product = collector.poll();
            outputDispatcher.output(product);
        }
        playhead.resetSought();
    }
    public void start(){playhead.setPlaying(true);}
    public void stop(){playhead.setPlaying(false);}
    public void seek(long time){playhead.seek(time);}
    public boolean isSought(){return playhead.isSought();}
    public long getTime(){return playhead.getTime();}

    public OutputDispatcher getOutputDispatcher() {
        return outputDispatcher;
    }
    public Playhead getPlayhead() {
        return playhead;
    }
}
