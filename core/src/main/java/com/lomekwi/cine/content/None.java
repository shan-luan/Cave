package com.lomekwi.cine.content;

import com.lomekwi.cine.pipeline.Processor;

public class None extends Element{
    public final static None INSTANCE = new None();
    @Override
    public Processor getNextProcessor() {
        return null;
    }
}
