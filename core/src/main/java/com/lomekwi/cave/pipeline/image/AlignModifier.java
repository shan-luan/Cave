package com.lomekwi.cave.pipeline.image;

import com.lomekwi.cave.pipeline.Modifier;
import com.lomekwi.cave.pipeline.Param;
import com.lomekwi.cave.pipeline.Source;

import java.util.List;

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

    public void setHAlign(HAlign hAlign) {
        this.hAlign = hAlign;
    }

    public void setVAlign(VAlign vAlign) {
        this.vAlign = vAlign;
    }

    public void setAlign(HAlign hAlign, VAlign vAlign) {
        setHAlign(hAlign);
        setVAlign(vAlign);
    }

    @Override
    public String getName() {
        return "对齐";
    }

    @Override
    public <F extends Transformable> F modify(F frame, long time) {
        Transform t = frame.getTransform();
        t.applyLocal(
            hAlign.offset(frame.getBaseWidth()),
            vAlign.offset(frame.getBaseHeight()),
            1f, 1f, 0f, false, false);
        return frame;
    }

    @Override
    public List<Param<?>> getParams() {
        return List.of(
            new Param.Choice("水平对齐",
                new String[]{"左", "中", "右"},
                () -> hAlign.ordinal(), i -> setHAlign(HAlign.values()[i]))
                .undo(Param.UndoMode.IMMEDIATE),
            new Param.Choice("垂直对齐",
                new String[]{"上", "中", "下"},
                () -> vAlign.ordinal(), i -> setVAlign(VAlign.values()[i]))
                .undo(Param.UndoMode.IMMEDIATE)
        );
    }
}
