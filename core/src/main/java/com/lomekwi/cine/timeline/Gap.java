package com.lomekwi.cine.timeline;

import com.lomekwi.cine.content.None;

public class Gap extends Seg {
    public Gap(long start) {
        super(None.INSTANCE, start);
    }
    @Override
    public boolean isGap() {return true;}
    @Override
    public long getEnd() {
        return track.getLastSeg()==this ? Long.MAX_VALUE : super.getEnd();
    }
    @Override
    public long getDuration() {
        return track.getLastSeg()==this ? Long.MAX_VALUE : super.getDuration();
    }
}
