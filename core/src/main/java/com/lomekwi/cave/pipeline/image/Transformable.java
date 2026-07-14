package com.lomekwi.cave.pipeline.image;

public interface Transformable extends Renderable {
    Transform getTransform();
    void setTransform(Transform transform);
    float getBaseWidth();
    float getBaseHeight();
    default void reset() {
        Transform t = getTransform();
        t.reset(0, 0, getBaseWidth(), getBaseHeight());
    }
}
