package com.lomekwi.cave.pipeline.num;

import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.timeline.Track;

public class NumFrame extends Frame {
    private double val;
    public NumFrame(Track track) {
        super(track);
    }

    public NumFrame(Track track, Source<?> source) {
        super(track, source);
    }

    public double getVal() {
        return val;
    }

    public void setVal(double val) {
        this.val = val;
    }
}
