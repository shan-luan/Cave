package com.lomekwi.cave.timeline;

import com.lomekwi.cave.pipeline.audio.AudClipSrc;
import com.lomekwi.cave.resource.media.AudRes;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;
import com.lomekwi.cave.ui.editpanel.tlarea.AudSegActor;

public class AudSeg extends Segment {
    public AudSeg(AudRes source) {
        super(new AudClipSrc(source));
    }

    @Override
    protected SegActor setupActor() {
        return new AudSegActor(this);
    }
}
