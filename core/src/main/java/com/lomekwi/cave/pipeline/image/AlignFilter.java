package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.detail.AlignFilterActor;

public class AlignFilter extends TransFilter {
    private HAlign hAlign = HAlign.LEFT;
    private VAlign vAlign = VAlign.BOTTOM;

    public AlignFilter(Source<?> source) {
        super(source, 0, 0, 1, 1, 0, false, false);
    }

    public enum HAlign {
        LEFT(0f), CENTER(-0.5f), RIGHT(-1f);
        private final float offsetFactor;

        HAlign(float offsetFactor) {
            this.offsetFactor = offsetFactor;
        }

        public float offset(float base) {
            return base * offsetFactor;
        }
    }

    public enum VAlign {
        TOP(-1f), MIDDLE(-0.5f), BOTTOM(0f);
        private final float offsetFactor;

        VAlign(float offsetFactor) {
            this.offsetFactor = offsetFactor;
        }

        public float offset(float base) {
            return base * offsetFactor;
        }
    }

    public HAlign getHAlign() {
        return hAlign;
    }

    public VAlign getVAlign() {
        return vAlign;
    }

    public void setAlign(HAlign hAlign, VAlign vAlign) {
        this.hAlign = hAlign;
        this.vAlign = vAlign;
    }

    @Override
    public String getName() {
        return "对齐";
    }

    @Override
    public void filter(Transformable frame, long time) {
        Transform t = frame.getTransform();
        t.applyLocal(
            hAlign.offset(frame.getBaseWidth()) + dx.getFloat(),
            vAlign.offset(frame.getBaseHeight()) + dy.getFloat(),
            scaleX.getFloat(), scaleY.getFloat(), dRotation.getFloat(),
            flipX(), flipY());
    }

    @Override
    protected Actor newActor() {
        return new AlignFilterActor(this);
    }
}
