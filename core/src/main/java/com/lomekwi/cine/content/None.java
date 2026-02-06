package com.lomekwi.cine.content;

import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.timeline.Segment;

public class None extends Element{
    public final static None INSTANCE = new None();
    @Override
    public Processor getNextProcessor() {
        return null;
    }
}
