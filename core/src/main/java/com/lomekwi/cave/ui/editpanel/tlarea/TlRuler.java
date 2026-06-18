package com.lomekwi.cave.ui.editpanel.tlarea;

import static com.lomekwi.cave.util.Units.SECOND;
import static com.lomekwi.cave.util.Units.niceScale;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.kotcrab.vis.ui.VisUI;
import com.lomekwi.cave.app.App;

public class TlRuler extends Widget {
    private static final float PIXELS_PER_TICK = 200f;
    private final TlGroup tlGroup;
    private final BitmapFont font = VisUI.getSkin().getFont("default-font");
    private final StringBuilder sb = new StringBuilder(8);
    private static final Color MORE_DARK_GRAY = new Color(0.2f, 0.2f, 0.2f, 1f);

    public TlRuler(TlGroup tlGroup) {
        super();
        this.tlGroup = tlGroup;
    }

    @Override
    public float getPrefHeight() {
        return 16;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        App.root.getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), MORE_DARK_GRAY);

        final long interval = niceScale((long) (tlGroup.view.durationTime * PIXELS_PER_TICK / getWidth()));
        final long start = (tlGroup.view.startTime / interval) * interval;

        for (long t = start; t < tlGroup.view.startTime + tlGroup.view.durationTime; t += interval) {
            float x = tlGroup.absoluteTimeToX(t) + getX();
            App.root.getShapeDrawer().filledRectangle(x, getY(), 1, getHeight(), Color.WHITE);
            formatTime(t, interval);
            font.draw(batch, sb, x + 2, getY() + getHeight() - 2);
        }
    }

    private void formatTime(long t, long interval) {
        long seconds = t / SECOND;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        sb.setLength(0);
        if (minutes < 10) sb.append('0');
        sb.append(minutes).append(':');
        if (seconds < 10) sb.append('0');
        sb.append(seconds);
        if (interval < SECOND) {
            long cs = (t % SECOND) / 10000;
            sb.append('.');
            if (cs < 10) sb.append('0');
            sb.append(cs);
        }
    }
}
