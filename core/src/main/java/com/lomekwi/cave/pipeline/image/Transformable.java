package com.lomekwi.cave.pipeline.image;

public interface Transformable<T extends Transformable<T>> {
    Transform getTransform();
    T setTransform(Transform transform);
}
