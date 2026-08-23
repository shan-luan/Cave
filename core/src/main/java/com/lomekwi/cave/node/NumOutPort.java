package com.lomekwi.cave.node;

import com.lomekwi.cave.pipeline.num.NumFrame;

public abstract class NumOutPort extends Node.OutPort<NumFrame>{
    private final NumFrame val=new NumFrame(null);

    @Override
    public NumFrame getData() {
        val.setVal(getVal());
        return val;
    }
    protected abstract double getVal();
}
