package com.lomekwi.cave.pipeline;

import com.badlogic.gdx.scenes.scene2d.Actor;

import java.io.Serial;
import java.io.Serializable;

public abstract class Filter<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Source<?> source;
    private transient Actor actor;

    public Filter(Source<?> source) {
        this.source = source;
    }

    public Source<?> getSource() {
        return source;
    }

    public abstract void filter(T product);

    public abstract String getName();

    public Actor getActor() {
        if (actor == null) {
            actor = newActor();
        }
        return actor;
    }

    protected abstract Actor newActor();

    public void invalidateDetailActor() {
        actor = null;
    }
}
