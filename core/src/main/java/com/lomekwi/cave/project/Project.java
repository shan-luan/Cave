package com.lomekwi.cave.project;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.badlogic.gdx.Gdx;
import com.google.common.eventbus.EventBus;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private transient Map<Track, Future<?>> trackFutures = new HashMap<>();
    private transient ExecutorService trackExecutor = Executors.newCachedThreadPool();

    protected Project() {
        Vars.appEventBus.register(this);
        name = i18n("未命名");
        projEventBus = new EventBus(uuid.toString());
        projEventBus.register(new AudioFrameListener());
        startTrackLoops();
    }

    public void update() {
        playhead.update();
        if(timeline.getTracks().size()!=trackCount){
            trackCount = timeline.getTracks().size();
            startTrackLoops();
        }
    }

    private void startTrackLoops() {
        for (Track track : timeline.getTracks()) {
            if (trackFutures.containsKey(track)) continue;
            Future<?> future = trackExecutor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    updateTrack(track);
                    LockSupport.parkNanos(1000000L);
                }
            });
            trackFutures.put(track, future);
        }
    }


    private void updateTrack(Track track) {
        Frame frame = track.get(playhead.getTime());
        if (frame != null) {
            projEventBus.post(track.lastFrameEndEvent);
            projEventBus.post(frame);
            track.getFramePhaser().arriveAndAwaitAdvance();
        }else {
            projEventBus.post(track.noFrameNowEvent);
        }
    }

    public void close() {
        Vars.appEventBus.unregister(this);
        for (Future<?> future : trackFutures.values()) {
            future.cancel(true);
        }
        trackExecutor.shutdown();
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
        trackFutures = new HashMap<>();
        trackExecutor = Executors.newCachedThreadPool();
        startTrackLoops();
    }

    public Path getSavePath() {
        return savePath;
    }
}
