package com.lomekwi.cave.project;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.google.common.eventbus.EventBus;
import com.lomekwi.cave.pipeline.PipelineEvents;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.timeline.SegFactory;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.app.Vars;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Project implements Serializable, AutoCloseable {
    private static final long serialVersionUID = 1L;
    protected transient Path savePath;
    public final Timeline timeline=new Timeline();
    public final Playhead playhead=new Playhead();
    public final Map<File,Resource> resources=new HashMap<>();
    public final SegFactory segFactory=new SegFactory(this);
    public transient EventBus projEventBus;
    public String name;
    public final UUID uuid=UUID.randomUUID();
    private transient ExecutorService executorService = Executors.newSingleThreadExecutor();
    private transient Future<?> updateFuture = null;
    private transient boolean seekTask = false;
    protected Project(){
        Vars.appEventBus.register(this);
        name=i18n("未命名");
        projEventBus=new EventBus(uuid.toString());
    }
    public void update() {
        if(playhead.getStates().contains(com.lomekwi.cave.timeline.playback.PlaybackState.SEEKING)){
            if(seekTask){
                if(updateFuture != null && updateFuture.isDone()){
                    playhead.clearState(com.lomekwi.cave.timeline.playback.PlaybackState.SEEKING);
                    seekTask = false;
                }
            }else {
                if(updateFuture != null) {
                    updateFuture.cancel(true);
                }
                updateFuture = executorService.submit(this::updateInternal);
                seekTask = true;
            }
        }
        playhead.update();
        if (updateFuture == null || updateFuture.isDone()) {
            updateFuture = executorService.submit(this::updateInternal);
        }
    }
    private void updateInternal() {
        projEventBus.post(PipelineEvents.LastFrameEndEvent.INSTANCE);
        for(Track track:timeline.getTracks()){
            if(!Thread.currentThread().isInterrupted()) {
                Frame frame = track.get(playhead.getTime());
                if (frame != null) {
                    projEventBus.post(frame);
                }
            }else {
                return;
            }
        }
    }
    public void close() {
        Vars.appEventBus.unregister(this);
        resources.values().forEach(resource -> {
            try {
                resource.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        // 关闭线程池并等待当前任务完成
        executorService.shutdown();
        try {
            if (updateFuture != null && !updateFuture.isDone()) {
                updateFuture.get(); // 等待当前更新任务完成
            }
        } catch (Exception e) {
            throw new RuntimeException("Error waiting for update task to complete", e);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        Vars.appEventBus.register(this);
        projEventBus = new EventBus(uuid.toString());
        executorService = Executors.newSingleThreadExecutor();
        updateFuture = null;
        seekTask = false;
    }
    public Path getSavePath() {
        return savePath;
    }
}
