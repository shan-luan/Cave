package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cave.pipeline.Filter;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.detail.TransFilterActor;

public class TransFilter extends Filter<Transformable> {
    public Filter.Val dx, dy, scaleX, scaleY, dRotation;
    private boolean flipX, flipY;
    public TransFilter(Source<?> source, float dx, float dy, float scaleX, float scaleY, float dRotation, boolean flipX, boolean flipY) {
        super(source);
        this.dx = new Filter.FixVal(); this.dx.set(dx);
        this.dy = new Filter.FixVal(); this.dy.set(dy);
        this.scaleX = new Filter.FixVal(); this.scaleX.set(scaleX);
        this.scaleY = new Filter.FixVal(); this.scaleY.set(scaleY);
        this.dRotation = new Filter.FixVal(); this.dRotation.set(dRotation);
        this.flipX = flipX;
        this.flipY = flipY;
    }

    public boolean flipX() { return flipX; }
    public boolean flipY() { return flipY; }
    public void flipX(boolean v) { flipX = v; }
    public void flipY(boolean v) { flipY = v; }

    @Override
    public String getName() { return "变换"; }

    @Override
    public void filter(Transformable frame, long time) {
        Transform t = frame.getTransform();
        t.applyLocal(dx.getFloat(), dy.getFloat(), scaleX.getFloat(), scaleY.getFloat(), dRotation.getFloat(), flipX, flipY);
    }

    @Override
    protected Actor newActor() {
        return new TransFilterActor(this);
    }
}
