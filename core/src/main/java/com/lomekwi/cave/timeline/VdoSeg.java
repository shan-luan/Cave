package com.lomekwi.cave.timeline;

import com.lomekwi.cave.pipeline.image.VdoClipSrc;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;
import com.lomekwi.cave.ui.editpanel.tlarea.VdoSegActor;

public class VdoSeg extends Segment {
    private final VdoRes vdoRes;

    public VdoSeg(VdoRes source){
        super(new VdoClipSrc(source));
        this.vdoRes = source;
    }

    public VdoRes getVdoRes() {
        return vdoRes;
    }

    @Override
    protected SegActor setupActor() {
        return new VdoSegActor(this);
    }
}
