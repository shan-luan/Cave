package com.lomekwi.cave.pipeline;

import com.badlogic.gdx.scenes.scene2d.Actor;

import java.io.Serializable;

public interface Filter<T> extends Serializable {
    void filter(T product);
    String getDisplayName();
    default Actor getDetailActor() { return null; }
}
