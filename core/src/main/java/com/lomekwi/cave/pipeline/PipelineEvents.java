package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.timeline.Track;

public class PipelineEvents {
    public static class LastFrameEndEvent {
        public final Track track;
        public LastFrameEndEvent(Track track) {
            this.track = track;
        }
    }
    public static class NoFrameNowEvent {
        public final Track track;
        public NoFrameNowEvent(Track track) {
            this.track = track;
        }
    }
}
