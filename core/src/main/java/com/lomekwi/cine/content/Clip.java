package com.lomekwi.cine.content;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.resource.media.MedRes;


public class Clip<T extends MedRes> extends Element{
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
    public Processor getNextProcessor() {
        return source.getNextProcessor();
    }
}
