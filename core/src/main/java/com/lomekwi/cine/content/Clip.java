package com.lomekwi.cine.content;


import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.decode.Decoder;
import com.lomekwi.cine.resource.Media;
import com.lomekwi.cine.timeline.Segment;


public class Clip<T extends Media<?>> extends Element{
    private final T source;
    private final long inPoint;

    public Clip(T source, long inPoint) {
        this.source = source;
        this.inPoint = inPoint;
    }

    public T getSource() {
        return source;
    }

    public long getInPoint() {
        return inPoint;
    }

    @Override
    public Decoder<?> getNextProcessor() {
        return source.getDecoder();
    }
}
