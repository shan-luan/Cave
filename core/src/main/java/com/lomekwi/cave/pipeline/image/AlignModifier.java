package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cave.pipeline.Modifier;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.ui.editpanel.detail.AlignModifierActor;

public class AlignModifier extends Modifier<Transformable> {
    private HAlign hAlign = HAlign.LEFT;
    private VAlign vAlign = VAlign.BOTTOM;

    public AlignModifier(Source<?> source) {
        super(source);
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
    public void modify(Transformable frame, long time) {
        Transform t = frame.getTransform();
        t.applyLocal(
            hAlign.offset(frame.getBaseWidth()),
            vAlign.offset(frame.getBaseHeight()),
            1f, 1f, 0f, false, false);
    }

    @Override
    protected Actor newActor() {
        return new AlignModifierActor(this);
    }
}
