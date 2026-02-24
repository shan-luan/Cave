package com.lomekwi.cave.pipeline.image;

import com.lomekwi.cave.pipeline.Filter;

public class TransFilter implements Filter<Transformable> {
    private final float dx,dy,scaleX,scaleY,dRotation;
    public TransFilter(float dx, float dy, float scaleX, float scaleY, float dRotation) {
        this.dx = dx;
        this.dy = dy;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.dRotation = dRotation;
    }

    @Override
    public void filter(Transformable product) {
        Transform t = product.getTransform();
        t.x += dx;
        t.y += dy;
        t.width *= scaleX;
        t.height *= scaleY;
        t.rotation += dRotation;
    }
}
