package com.lomekwi.cine.timeline;

import com.lomekwi.cine.content.None;

public class Gap extends Segment{
    public Gap(long start, long duration) {
        super(None.INSTANCE, start, duration);
    }
}
