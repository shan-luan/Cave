package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.timeline.Track;

public class NoFrameNowEvent {
    public final Track track;
    
    public NoFrameNowEvent(Track track) {
        this.track = track;
    }
}
