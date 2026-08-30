package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.pipeline.num.NumFrame;

import java.util.Set;

public class NumInPort extends Node.InPort<NumFrame> {

    public NumInPort(String name) {
        this(name, new NumFrame(null));
    }

    public NumInPort(String name, NumFrame defaultValue) {
        super(name);
        setDefaultData(defaultValue);
    }

    @Override
    public Set<Class<?>> getConstraint() {
        return Set.of(NumFrame.class);
    }
}
