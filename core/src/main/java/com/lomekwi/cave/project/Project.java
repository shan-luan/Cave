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
    public final Timeline timeline;
    public transient Playhead playhead;
    public final Map<File, Resource> resources = new HashMap<>();
    public final SegFactory segFactory = new SegFactory(this);
    public transient EventBus projEventBus;
    public String name;
    public final UUID uuid = UUID.randomUUID();

    private transient boolean isActive = false;

    protected Project() {
        Vars.appEventBus.register(this);
        name = i18n("未命名");
        projEventBus = new EventBus(uuid.toString());
        projEventBus.register(new AudioFrameListener());
        projEventBus.register(this);
        timeline = new Timeline(this);
        playhead = new Playhead(projEventBus);
    }

    public void update() {
        if (!isActive) {
            return;
        }

        for (Track track : timeline.getTracks()) {
            if (track.getFuture() == null || track.getFuture().isDone()) {
                Future<?> future = Vars.trackExecutor.submit(track);
                track.setFuture(future);
            }
        }
    }

    @Subscribe
    public void onProjectFronted(ProjectFrontedEvent event) {
        if (!isActive) {
            isActive = true;
            Gdx.app.log("Project", "项目 [" + name + "] 激活，开始轨道循环");
            update();
        }
    }

    @Subscribe
    public void onProjectBackgrounded(ProjectBackgroundedEvent event) {
        if (isActive) {
            isActive = false;
            Gdx.app.log("Project", "项目 [" + name + "] 后台化，停止轨道循环");
            stopTrackLoops();
        }
    }

    private void stopTrackLoops() {
        for (Track track : timeline.getTracks()) {
            Future<?> future = track.getFuture();
            if (future != null) {
                future.cancel(true);
                track.setFuture(null);
            }
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
        playhead = new Playhead(projEventBus);
        isActive = false;
    }

    public Path getSavePath() {
        return savePath;
    }
}
