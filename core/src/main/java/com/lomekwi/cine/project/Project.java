package com.lomekwi.cine.project;

import com.lomekwi.cine.pipeline.Distributor;
import com.lomekwi.cine.timeline.Timeline;
import com.lomekwi.cine.timeline.playback.Playhead;

public class Project {
    public final Timeline timeline=new Timeline();
    public final Distributor distributor=new Distributor(timeline);
    public final Playhead playhead=new Playhead();
}
