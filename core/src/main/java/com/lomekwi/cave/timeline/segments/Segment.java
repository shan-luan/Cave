package com.lomekwi.cave.timeline.segments;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cave.pipeline.Product;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.tlarea.SegActor;

public abstract class Segment<T extends Product> {
    private final Source<T> source;
    private final SegActor actor;
    protected Segment(Source<T> source, SegActor actor) {
        this.source = source;
        this.actor = actor;
    }

    public Source<T> getSource() {
        return source;
    }

    public SegActor getActor() {
        return actor;
    }
}
