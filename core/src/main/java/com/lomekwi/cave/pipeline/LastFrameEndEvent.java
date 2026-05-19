package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.timeline.Track;

public class LastFrameEndEvent {
    public final Track track;
    
    public LastFrameEndEvent(Track track) {
        this.track = track;
    }
}
