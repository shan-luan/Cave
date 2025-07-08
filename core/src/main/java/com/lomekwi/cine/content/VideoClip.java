package com.lomekwi.cine.content;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.pipeline.decode.VideoDecoder;
import com.lomekwi.cine.resource.Video;

public class VideoClip implements Element {
    private final long inPoint;
    private final long duration;
    private final long start;
    private final Video source;

    public VideoClip(Video source, long inPoint, long duration, long start) {
        this.inPoint = inPoint;
        this.duration = duration;
        this.start = start;
        this.source = source;
        //source.incrementCount();
    }

    @Override
    public Processor getNextProcessor() {
        //TODO:从源中获取解码器
        return null;
    }
    @Override
    public void dispose() {
        //source.decrementCount();
    }

    public long getInPoint() {
        return inPoint;
    }

    @Override
    public long getStart() {
        return start;
    }

    @Override
    public long getEnd() {
        return start+ duration;
    }
}
