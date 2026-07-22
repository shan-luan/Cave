package com.lomekwi.cave.ui.editpanel.tlarea;

import static com.lomekwi.cave.util.Units.niceScale;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.resource.media.ImgRes;
import com.lomekwi.cave.ui.Colors;
import com.lomekwi.cave.timeline.ImgSeg;
import com.lomekwi.cave.timeline.Segment;
import space.earlygrey.shapedrawer.ShapeDrawer;

public class ImgSegActor extends SegActor {

    public ImgSegActor(Segment segment) {
        super(segment);
    }

    @Override
    public void drawContent(Batch batch, float parentAlpha, float visibleStartX, float visibleEndX) {
        ShapeDrawer sd = App.root.getShapeDrawer();
        Segment seg = getSegment();
        var range = seg.getRange();
        long segLocalStart = range.lowerEndpoint() - seg.getOrigin();
        long segLocalEnd = range.upperEndpoint() - seg.getOrigin();
        long segDuration = segLocalEnd - segLocalStart;

        sd.filledRectangle(getX(), getY(), getWidth(), getHeight(), Colors.ACCENT_LIGHT);

        if (segDuration > 0) {
            ImgRes res = ((ImgSeg) getSegment()).getImgRes();

            float pxPerUs = getWidth() / (float) segDuration;
            float aspect = (float) res.getWidth() / res.getHeight();
            float thumbDisplayW = getHeight() * aspect;

            long rawStep = (long)(thumbDisplayW / pxPerUs);
            if (rawStep <= 0) rawStep = 1;
            long timeStep = niceScale(rawStep);

            long gridOrigin = (segLocalStart / timeStep) * timeStep;
            long absVisibleStart = segLocalStart + (long)(visibleStartX / pxPerUs);
            long absVisibleEnd   = segLocalStart + (long)(visibleEndX   / pxPerUs);
            long firstT = ((absVisibleStart - gridOrigin) / timeStep) * timeStep + gridOrigin;
            firstT = Math.max(gridOrigin, firstT);
            long lastT = Math.min(segLocalEnd, absVisibleEnd);

            float lastRightEdge = Float.NEGATIVE_INFINITY;

            for (long t = firstT; t < lastT; t += timeStep) {
                Texture tex = res.getPreview(t);
                if (tex == null) continue;

                float x = getX() + (t - segLocalStart) * pxPerUs;

                if (x + thumbDisplayW <= lastRightEdge) continue;

                batch.draw(tex, x, getY(), thumbDisplayW, getHeight());
                lastRightEdge = x + thumbDisplayW;
            }
        }
    }
}
