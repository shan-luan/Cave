package com.lomekwi.cine.pipeline.image;

public interface Transformable<T extends Transformable<T>> {
    Transform getTransform();
    T setTransform(Transform transform);
}
