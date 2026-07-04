package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.detail.TransFilterActor;

public class TransFilter extends Filter<Transformable> {
    private float dx,dy,scaleX,scaleY,dRotation;
    public TransFilter(Source<?> source, float dx, float dy, float scaleX, float scaleY, float dRotation) {
        super(source);
        this.dx = dx;
        this.dy = dy;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.dRotation = dRotation;
    }

    public float dx() { return dx; }
    public float dy() { return dy; }
    public float scaleX() { return scaleX; }
    public float scaleY() { return scaleY; }
    public float dRotation() { return dRotation; }
    public void dx(float v) { dx = v; }
    public void dy(float v) { dy = v; }
    public void scaleX(float v) { scaleX = v; }
    public void scaleY(float v) { scaleY = v; }
    public void dRotation(float v) { dRotation = v; }

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
    }

    @Override
    protected Actor newActor() {
        return new TransFilterActor(this);
    }
}
