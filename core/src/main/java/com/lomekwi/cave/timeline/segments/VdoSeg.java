package com.lomekwi.cave.timeline.segments;

import com.lomekwi.cave.pipeline.image.ImgProd;
import com.lomekwi.cave.pipeline.image.VdoClipSrc;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.ui.editpanel.tlarea.segactors.VdoSegActor;

public class VdoSeg extends Segment<ImgProd> {
    public VdoSeg(VdoRes source,long offset){
        super(new VdoClipSrc(source,offset),new VdoSegActor());
    }
}
