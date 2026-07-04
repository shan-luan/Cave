package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.detail.TransFilterActor;

public class TransFilter extends Filter<Transformable> {
    private float dx,dy,scaleX,scaleY,dRotation,pivotX,pivotY;
    private boolean flipX,flipY;
    public TransFilter(Source<?> source, float dx, float dy, float scaleX, float scaleY, float dRotation, float pivotX, float pivotY, boolean flipX, boolean flipY) {
        super(source);
        this.dx = dx;
        this.dy = dy;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.dRotation = dRotation;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.flipX = flipX;
        this.flipY = flipY;
    }

    public float dx() { return dx; }
    public float dy() { return dy; }
    public float scaleX() { return scaleX; }
    public float scaleY() { return scaleY; }
    public float dRotation() { return dRotation; }
    public float pivotX() { return pivotX; }
    public float pivotY() { return pivotY; }
    public boolean flipX() { return flipX; }
    public boolean flipY() { return flipY; }
    public void dx(float v) { dx = v; }
    public void dy(float v) { dy = v; }
    public void scaleX(float v) { scaleX = v; }
    public void scaleY(float v) { scaleY = v; }
    public void dRotation(float v) { dRotation = v; }
    public void pivotX(float v) { pivotX = v; }
    public void pivotY(float v) { pivotY = v; }
    public void flipX(boolean v) { flipX = v; }
    public void flipY(boolean v) { flipY = v; }

    @Override
    public String getName() { return "变换滤镜"; }

    @Override
    public void filter(Transformable product) {
        Transform t = product.getTransform();
        t.x += dx;
        t.y += dy;
        t.width *= scaleX;
        t.height *= scaleY;
        t.rotation += dRotation;
        t.pivotX += pivotX;
        t.pivotY += pivotY;
        if (flipX) t.flipX = !t.flipX;
        if (flipY) t.flipY = !t.flipY;
    }

    @Override
    protected Actor newActor() {
        return new TransFilterActor(this);
    }
}
