package com.lomekwi.cave.project;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.audio.AudioFrameListener;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;

public class Project implements Serializable, AutoCloseable {
    private static final long serialVersionUID = 1L;
    protected transient Path savePath;
    public final Timeline timeline = new Timeline();
    public final Playhead playhead = new Playhead();
    public final Map<File, Resource> resources = new HashMap<>();
    public final SegFactory segFactory = new SegFactory(this);
    public transient EventBus projEventBus;
    public String name;
    public final UUID uuid = UUID.randomUUID();
    private int trackCount;

    private transient boolean isActive = false;
    private transient List<Future<?>> trackFutures = new ArrayList<>();

    protected Project() {
        Vars.appEventBus.register(this);
        name = i18n("未命名");
        projEventBus = new EventBus(uuid.toString());
        projEventBus.register(new AudioFrameListener());
        projEventBus.register(this);
    }

    public void update() {
        int currentTrackCount = timeline.getTracks().size();
        if(currentTrackCount != trackCount){
            if (isActive && currentTrackCount > trackCount) {
                for (int i = trackCount; i < currentTrackCount; i++) {
                    Track track = timeline.getTracks().get(i);
                    Future<?> future = Vars.trackExecutor.submit(() -> {
                        Gdx.app.log("Project", "轨道线程启动: " + track);
                        try {
                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    updateTrack(track);
                                }catch (Exception e){
                                    Gdx.app.error("Project", "在更新轨道时发生错误", e);
                                    LockSupport.parkNanos(1000000L);
                                }
                            }
                        } finally {
                            Gdx.app.log("Project", "轨道线程结束: " + track);
                        }
                    });
                    trackFutures.add(future);
                }
            }
            trackCount = currentTrackCount;
        }
    }

    @Subscribe
    public void onProjectFronted(ProjectEvents.ProjectFrontedEvent event) {
        if (!isActive) {
            isActive = true;
            Gdx.app.log("Project", "项目 [" + name + "] 激活，开始轨道循环");
            startTrackLoops();
        }
    }

    @Subscribe
    public void onProjectBackgrounded(ProjectEvents.ProjectBackgroundedEvent event) {
        if (isActive) {
            isActive = false;
            Gdx.app.log("Project", "项目 [" + name + "] 后台化，停止轨道循环");
            stopTrackLoops();
        }
    }

    private void startTrackLoops() {
        for (Track track : timeline.getTracks()) {
            Future<?> future = Vars.trackExecutor.submit(() -> {
                Gdx.app.log("Project", "轨道线程启动: " + track);
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            updateTrack(track);
                        }catch (Exception e){
                            Gdx.app.error("Project", "在更新轨道时发生错误", e);
                            LockSupport.parkNanos(1000000L);
                        }
                    }
                } finally {
                    Gdx.app.log("Project", "轨道线程结束: " + track);
                }
            });
            trackFutures.add(future);
        }
    }

    private void stopTrackLoops() {
        for (Future<?> future : trackFutures) {
            future.cancel(true);
        }
        trackFutures.clear();
    }


    private void updateTrack(Track track) {
        Frame frame = track.get(playhead.getTime());
        if (frame != null) {
            projEventBus.post(track.getLastFrameEndEvent());
            projEventBus.post(frame);
            track.getFramePhaser().arriveAndAwaitAdvance();
        }else {
            projEventBus.post(track.getNoFrameNowEvent());
            LockSupport.parkNanos(1000000L);
        }
    }

    public void close() {
        Vars.appEventBus.unregister(this);
        isActive = false;
        stopTrackLoops();
        resources.values().forEach(resource -> {
            try {
                resource.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        Vars.appEventBus.register(this);
        projEventBus = new EventBus(uuid.toString());
        projEventBus.register(this);
        isActive = false;
        trackFutures = new ArrayList<>();
    }

    public Path getSavePath() {
        return savePath;
    }
}
