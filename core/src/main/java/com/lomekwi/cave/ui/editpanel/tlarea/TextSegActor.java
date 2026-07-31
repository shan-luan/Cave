package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.kotcrab.vis.ui.VisUI;
import com.lomekwi.cave.pipeline.text.TextSrc;
import com.lomekwi.cave.timeline.Segment;

public class TextSegActor extends SegActor {

    public TextSegActor(Segment segment) {
        super(segment);
    }

    @Override
    public void drawContent(Batch batch, float parentAlpha, float visibleStartX, float visibleEndX) {
        super.drawContent(batch, parentAlpha, visibleStartX, visibleEndX);
        String text = ((TextSrc) getSegment().getSource()).getText();
        if (text != null && !text.isEmpty()) {
            var font = VisUI.getSkin().getFont("default-font");
            float textY = getY() + getHeight() / 2f + font.getCapHeight() / 2f;
            font.draw(batch, text, getX() + visibleStartX + 4, textY);
        }
    }
}
