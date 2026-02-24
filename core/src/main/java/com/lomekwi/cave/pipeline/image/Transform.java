package com.lomekwi.cave.pipeline.image;

public class Transform {
    public float x, y, width, height, rotation;
    public Transform(float x, float y, float width, float height, float rotation) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
    }
    public Transform() {
        this(0, 0, 1, 1, 0);
    }
}
