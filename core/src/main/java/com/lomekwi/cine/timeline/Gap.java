package com.lomekwi.cine.timeline;

import com.lomekwi.cine.content.None;

public class Gap extends Seg {
    public Gap(long start, long duration) {
        super(None.INSTANCE, start, duration);
    }
    @Override
    public boolean isGap() {return true;}
}
