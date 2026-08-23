package com.lomekwi.cave.node;

import com.lomekwi.cave.pipeline.num.NumFrame;

import java.util.Set;

public abstract class NumInPort extends Node.InPort<NumFrame> {

    public NumInPort() {
        this(new NumFrame(null));
    }

    public NumInPort(NumFrame defaultValue) {
        setDefaultData(defaultValue);
    }

    @Override
    public Set<Class<?>> getConstraint() {
        return Set.of(NumFrame.class);
    }
}
