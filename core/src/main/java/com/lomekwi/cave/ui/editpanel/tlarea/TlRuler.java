package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.lomekwi.cave.ui.Root;

public class TlRuler extends Widget {
    private static final int TARGET_TICKS = 10;
    private final TlGroup tlGroup;
    public TlRuler(TlGroup tlGroup) {
        super();
        this.tlGroup=tlGroup;
    }
    @Override
    public float getPrefHeight() {
        return 20;
    }
    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        final long interval = (long) Math.pow(10, Math.floor(Math.log10(tlGroup.viewDurationTime / (double) TARGET_TICKS)));
        final long start = (tlGroup.viewStartTime / interval) * interval;
        for (long t = start; t < tlGroup.viewStartTime + tlGroup.viewDurationTime; t += interval) {
            float x = tlGroup.absoluteTimeToX(t);
            Root.getInstance().getShapeDrawer().filledRectangle(x, getY(), 1, getHeight(), Color.WHITE);
        }
    }
}
