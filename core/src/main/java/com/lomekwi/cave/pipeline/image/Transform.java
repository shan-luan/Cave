package com.lomekwi.cave.pipeline.image;

public class Transform {
    public float x, y, width, height, rotation;
    public float pivotX, pivotY;
    public boolean flipX, flipY;
    public Transform(float x, float y, float width, float height, float rotation, float pivotX, float pivotY) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.flipX = false;
        this.flipY = false;
    }
    public Transform(float x, float y, float width, float height, float rotation) {
        this(x, y, width, height, rotation, 0, 0);
    }
    public Transform() {
        this(0, 0, 1, 1, 0, 0, 0);
    }
}
