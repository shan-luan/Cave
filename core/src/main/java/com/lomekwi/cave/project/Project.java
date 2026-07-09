package com.lomekwi.cave.project;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.pipeline.audio.AudioFrameSink;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.timeline.SegFactory;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.UndoManager;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.app.App;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Future;

public class Project implements Serializable, AutoCloseable {
    @Serial
    private static final long serialVersionUID = 1L;
    protected transient Path savePath;
    public final Timeline timeline;
    public transient Playhead playhead;
    public final Multimap<File, Resource> resources = ArrayListMultimap.create();
    public final SegFactory segFactory = new SegFactory(this);
    public transient EventBus projEventBus;
    public transient UndoManager undoManager;
    public String name;
    public final UUID uuid = UUID.randomUUID();
    public long savedVersion = 0;
    public transient long currentVersion = 0;

    private transient boolean isActive = false;

    protected Project() {
        App.appEventBus.register(this);
        name = i18n("未命名");
        projEventBus = new EventBus(uuid.toString());
        undoManager = new UndoManager(this);
        projEventBus.register(new AudioFrameSink());
        projEventBus.register(this);
        timeline = new Timeline(this);
        playhead = new Playhead(projEventBus);
    }

    public void update() {
        if (!isActive) {
            return;
        }

        for (Track track : timeline.getTracks()) {
            if (track.getWorker().getFuture() == null || track.getWorker().getFuture().isDone()) {
                Future<?> future = App.workerExecutor.submit(track.getWorker());
                track.getWorker().setFuture(future);
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
            Future<?> future = track.getWorker().getFuture();
            if (future != null) {
                future.cancel(true);
                track.getWorker().setFuture(null);
            }
        }
    }


    public void close() {
        App.appEventBus.unregister(this);
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

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        currentVersion = savedVersion;
        segFactory.setProject(this);
        App.appEventBus.register(this);
        projEventBus = new EventBus(uuid.toString());
        projEventBus.register(new AudioFrameSink());
        projEventBus.register(this);
        playhead = new Playhead(projEventBus);
        undoManager = new UndoManager(this);
        isActive = false;
    }

    public Path getSavePath() {
        return savePath;
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        savedVersion = currentVersion;
        out.defaultWriteObject();
    }

    public boolean isDirty() {
        return currentVersion != savedVersion;
    }
}
