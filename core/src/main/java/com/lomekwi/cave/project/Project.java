package com.lomekwi.cave.project;

import com.lomekwi.cave.pipeline.Distributor;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.playback.Playhead;

import java.io.Serializable;

public class Project implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Timeline timeline=new Timeline();
    public final transient Distributor distributor=new Distributor(timeline);
    public final Playhead playhead=new Playhead();
}
