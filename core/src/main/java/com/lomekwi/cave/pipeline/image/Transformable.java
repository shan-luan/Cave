package com.lomekwi.cave.pipeline.image;

public interface Transformable extends Renderable {
    Transform getTransform();
    void setTransform(Transform transform);
}
