package com.lomekwi.cine.content;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.resource.media.MedRes;


public abstract class Clip<T extends MedRes> extends Element{
    private final T source;
    private final long offset;

    public Clip(T source, long offset) {
        this.source = source;
        this.offset = offset;
    }

    public T getSource() {
        return source;
    }

    public long getOffset() {
        return offset;
    }

    @Override
    public Processor getNextProcessor() {
        return source.getNextProcessor();
    }
}
