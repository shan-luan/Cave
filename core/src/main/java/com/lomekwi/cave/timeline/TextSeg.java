package com.lomekwi.cave.timeline;

import com.lomekwi.cave.pipeline.text.TextSrc;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;
import com.lomekwi.cave.ui.editpanel.tlarea.TextSegActor;

public class TextSeg extends Segment {

    public TextSeg() {
        super(new TextSrc());
    }

    public TextSeg(String text) {
        super(new TextSrc(text));
    }

    public TextSrc getTextSrc() {
        return (TextSrc) source();
    }

    @Override
    protected SegActor setupActor() {
        return new TextSegActor(this);
    }
}
