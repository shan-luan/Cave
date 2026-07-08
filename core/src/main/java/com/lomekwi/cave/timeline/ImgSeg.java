package com.lomekwi.cave.timeline;

import com.lomekwi.cave.pipeline.image.ImgSrc;
import com.lomekwi.cave.resource.media.ImgRes;
import com.lomekwi.cave.ui.editpanel.tlarea.ImgSegActor;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;

public class ImgSeg extends Segment {

    public ImgSeg(ImgRes source) {
        super(new ImgSrc(source));
    }

    public ImgRes getImgRes() {
        return ((ImgSrc) source()).getImgRes();
    }

    @Override
    protected SegActor setupActor() {
        return new ImgSegActor(this);
    }
}
