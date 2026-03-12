package com.lomekwi.cave.project;

import static com.lomekwi.cave.util.i18n.I18N.i18n;

import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.AppEvents;
import com.lomekwi.cave.pipeline.Distributor;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.util.Vars;

import java.io.Serializable;

public class Project implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Timeline timeline=new Timeline();
    public final transient Distributor distributor=new Distributor(timeline);
    public final Playhead playhead=new Playhead();
    public String name;
    protected Project(){
        Vars.appEventBus.register(this);
        name=i18n("未命名");
        playhead.setPlaying( true);
    }
    @Subscribe
    public void onUpdate(AppEvents.UpdateEvent event) {
        playhead.update();
        distributor.distribute(playhead.getTime());
    }
}
