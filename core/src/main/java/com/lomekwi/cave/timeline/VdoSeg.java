package com.lomekwi.cave.timeline;

import com.lomekwi.cave.pipeline.image.ImgProd;
import com.lomekwi.cave.pipeline.image.VdoClipSrc;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.ui.editpanel.tlarea.VdoSegActor;

public class VdoSeg extends Segment {
    public VdoSeg(VdoRes source){
        super(new VdoClipSrc(source));
        setActor(new VdoSegActor(this));
    }
}
