package com.lomekwi.cave.project;

import com.lomekwi.cave.pipeline.Distributor;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.playback.Playhead;

public class Project {
    public final Timeline timeline=new Timeline();
    public final Distributor distributor=new Distributor(timeline);
    public final Playhead playhead=new Playhead();
}
