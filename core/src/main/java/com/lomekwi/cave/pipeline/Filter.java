package com.lomekwi.cave.pipeline;

import com.badlogic.gdx.scenes.scene2d.Actor;

import java.io.Serial;
import java.io.Serializable;

public abstract class Filter<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Source<?> source;
    private transient Actor detailActor;

    public Filter(Source<?> source) {
        this.source = source;
    }

    public Source<?> getSource() {
        return source;
    }

    public abstract void filter(T product);

    public abstract String getDisplayName();

    public Actor getDetailActor() {
        if (detailActor == null) {
            detailActor = createDetailActor();
        }
        return detailActor;
    }

    protected abstract Actor createDetailActor();

    public void invalidateDetailActor() {
        detailActor = null;
    }
}
