package com.lomekwi.cave.node;

public abstract class NumOutPort extends Node.OutPort<double[]>{
    private final double[] val=new double[1];

    @Override
    public double[] getData() {
        val[0] = getVal();
        return val;
    }
    protected abstract double getVal();
}
