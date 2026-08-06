package com.lomekwi.cave.node;

import java.util.Set;

public abstract class NumInPort extends Node.InPort<double[]> {

    public NumInPort() {
        this(new double[0]);
    }

    public NumInPort(double[] defaultValue) {
        setDefaultData(defaultValue);
    }

    @Override
    public Set<Class<?>> getConstraint() {
        return Set.of(double[].class);
    }
}
