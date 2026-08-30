package com.lomekwi.cave.pipeline;

import com.lomekwi.cave.pipeline.num.NumFrame;

public abstract class NumOutPort extends Node.OutPort<NumFrame>{
    private final NumFrame val=new NumFrame(null);

    protected NumOutPort(String name) {
        super(name);
    }

    @Override
    public NumFrame getData() {
        val.setVal(getVal());
        return val;
    }

    @Override
    public Class<? extends NumFrame> getType() {
        return NumFrame.class;
    }

    protected abstract double getVal();
}
