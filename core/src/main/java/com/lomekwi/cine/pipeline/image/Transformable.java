package com.lomekwi.cine.pipeline.image;

public interface Transformable {
    Transform getTransform();
    void setTransform(Transform transform);
}
