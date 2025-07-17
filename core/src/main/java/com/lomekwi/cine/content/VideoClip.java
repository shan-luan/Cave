package com.lomekwi.cine.content;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.resource.Video;
import com.lomekwi.cine.timeline.playback.PlayController;
import com.lomekwi.cine.timeline.playback.Playhead;

public class VideoClip extends Element {
    private final long inPoint;
    private final Video source;

    public VideoClip(Video source, long inPoint, long duration, long start, Playhead playhead) {
        super(duration, start,playhead);

        this.inPoint = inPoint;
        this.source = source;

        source.add(this);

    }

    @Override
    public Processor getNextProcessor() {
        return source.getDecoder();
    }
    @Override
    public void dispose() {
        source.remove(this);
    }

    public long getInPoint() {
        return inPoint;
    }
}
