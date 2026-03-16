package com.lomekwi.cave.project;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.google.common.eventbus.EventBus;
import com.lomekwi.cave.pipeline.Distributor;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.resource.media.MedRes;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.util.Vars;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Project implements Serializable, AutoCloseable {
    private static final long serialVersionUID = 1L;
    public final Timeline timeline=new Timeline();
    public final transient Distributor distributor=new Distributor(timeline);
    public final Playhead playhead=new Playhead();
    public final Map<File,Resource> resources=new HashMap<>();
    public final transient EventBus projEventBus;
    public String name;
    public final UUID uuid=UUID.randomUUID();
    protected Project(){
        Vars.appEventBus.register(this);
        name=i18n("未命名");
        projEventBus=new EventBus(uuid.toString());
    }
    public void update() {
        playhead.update();
        distributor.distribute(playhead.getTime());
    }
    public void close() {
        Vars.appEventBus.unregister(this);
    }
}
