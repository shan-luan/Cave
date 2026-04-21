package com.lomekwi.cave.project;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.google.common.eventbus.EventBus;
import com.lomekwi.cave.pipeline.PipelineEvents;
import com.lomekwi.cave.pipeline.Product;
import com.lomekwi.cave.resource.Resource;
import com.lomekwi.cave.timeline.SegFactory;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.util.Vars;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Project implements Serializable, AutoCloseable {
    private static final long serialVersionUID = 1L;
    public final Timeline timeline=new Timeline();
    public final Playhead playhead=new Playhead();
    public final Map<File,Resource> resources=new HashMap<>();
    public final SegFactory segFactory=new SegFactory(this);
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
        // 每帧开始时发送清除事件
        projEventBus.post(PipelineEvents.LastFrameEndEvent.INSTANCE);
        for(Track track:timeline.getTracks()){
            Product prod = track.get(playhead.getTime());
            if(prod != null){
                projEventBus.post(prod);
            }
        }
    }
    public void close() {
        Vars.appEventBus.unregister(this);
    }
}
