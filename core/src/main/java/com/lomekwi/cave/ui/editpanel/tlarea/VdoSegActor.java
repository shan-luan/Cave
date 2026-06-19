package com.lomekwi.cave.ui.editpanel.tlarea;

import static com.lomekwi.cave.util.Units.niceScale;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.resource.media.VdoRes;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.VdoSeg;
import space.earlygrey.shapedrawer.ShapeDrawer;

public class VdoSegActor extends SegActor {

    public VdoSegActor(Segment segment) {
        super(segment);
    }

    @Override
    public void drawContent(Batch batch, float parentAlpha) {
        ShapeDrawer sd = App.root.getShapeDrawer();
        Segment seg = getSegment();
        var range = seg.getRange();
        long segLocalStart = range.lowerEndpoint() - seg.getOrigin();
        long segLocalEnd = range.upperEndpoint() - seg.getOrigin();
        long segDuration = segLocalEnd - segLocalStart;

        sd.filledRectangle(getX(), getY(), getWidth(), getHeight(), lightBlue);

        if (segDuration > 0) {
            VdoRes res = ((VdoSeg) getSegment()).getVdoRes();
            float pxPerUs = getWidth() / (float) segDuration;
            float aspect = (float) res.getWidth() / res.getHeight();
            float thumbDisplayW = getHeight() * aspect;

            long rawStep = (long)(thumbDisplayW / pxPerUs);
            if (rawStep <= 0) rawStep = 1;
            long timeStep = niceScale(rawStep);

            long firstT = Math.max(0, segLocalStart - timeStep);

            float lastRightEdge = Float.NEGATIVE_INFINITY;

            for (long t = firstT; t < segLocalEnd; t += timeStep) {
                Texture tex = res.getThumbnail(t);
                if (tex == null) continue;

                float x = getX() + (t - segLocalStart) * pxPerUs;

                if (x + thumbDisplayW <= lastRightEdge) continue;

                batch.draw(tex, x, getY(), thumbDisplayW, getHeight());
                lastRightEdge = x + thumbDisplayW;
            }
        }
    }
}
