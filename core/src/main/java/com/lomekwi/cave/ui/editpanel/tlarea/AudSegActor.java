package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.resource.media.AudRes;
import com.lomekwi.cave.timeline.AudSeg;
import com.lomekwi.cave.timeline.Segment;
import space.earlygrey.shapedrawer.ShapeDrawer;

public class AudSegActor extends SegActor {

    public AudSegActor(Segment segment) {
        super(segment);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        ShapeDrawer sd = App.root.getShapeDrawer();

        // 背景
        sd.filledRectangle(getX(), getY(), getWidth(), getHeight(), lightBlue);

        AudRes res = ((AudSeg) getSegment()).getAudRes();
        float[] peaks = res.getPeaks();
        Segment seg = getSegment();
        var range = seg.getRange();
        long segLocalStart = range.lowerEndpoint() - seg.getOrigin();
        long segDuration = range.upperEndpoint() - range.lowerEndpoint();

        // 绘制波形（仅当数据就绪）
        if (peaks != null && peaks.length > 0 && segDuration > 0) {
            float centerY = getY() + getHeight() / 2;
            float halfH = getHeight() / 2;
            float bucketUs = res.getBucketDuration();
            int pixWidth = (int) getWidth();

            for (int px = 0; px < pixWidth; px++) {
                long pxStart = segLocalStart + px * segDuration / pixWidth;
                long pxEnd = segLocalStart + (px + 1) * segDuration / pixWidth;

                int startIdx = (int)(pxStart / bucketUs);
                int endIdx = (int)(pxEnd / bucketUs);
                startIdx = Math.max(0, Math.min(startIdx, peaks.length - 1));
                endIdx = Math.max(0, Math.min(endIdx, peaks.length - 1));

                float maxPeak = 0;
                for (int pi = startIdx; pi <= endIdx; pi++) {
                    if (peaks[pi] > maxPeak) maxPeak = peaks[pi];
                }

                float barH = maxPeak * halfH;
                float pxX = getX() + px;
                sd.line(pxX, centerY - barH, pxX, centerY + barH, waveColor, 1);
            }
        }

        // 边框始终绘制
        sd.rectangle(getX(), getY(), getWidth(), getHeight(), blue, 2);
    }
}
