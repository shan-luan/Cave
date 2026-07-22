package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.lomekwi.cave.resource.media.AudRes;
import com.lomekwi.cave.timeline.AudSeg;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.ui.Colors;

public class AudSegActor extends SegActor {

    private static ShaderProgram waveShader;

    private static ShaderProgram getWaveShader() {
        if (waveShader == null) {
            waveShader = new ShaderProgram(VERT, FRAG);
            if (!waveShader.isCompiled()) {
                Gdx.app.error("AudSegActor", "Wave shader failed:\n" + waveShader.getLog());
            }
        }
        return waveShader;
    }

    public AudSegActor(Segment segment) {
        super(segment);
    }

    @Override
    protected void drawContent(Batch batch, float parentAlpha, float visibleStartX, float visibleEndX) {
        super.drawContent(batch, parentAlpha, visibleStartX, visibleEndX);

        AudRes res = ((AudSeg) getSegment()).getAudRes();
        AudRes.Waveformer wf = res.waveformer();
        Texture waveTex = wf.waveTex;
        if (waveTex == null) return;

        Segment seg = getSegment();
        var range = seg.getRange();
        long segLocalStart = range.lowerEndpoint() - seg.getOrigin();
        long segLocalEnd = range.upperEndpoint() - seg.getOrigin();
        long segDuration = segLocalEnd - segLocalStart;
        if (segDuration <= 0) return;

        long step = wf.bucketDuration;
        float pxPerUs = getWidth() / (float) segDuration;
        long gridOrigin = (segLocalStart / step) * step;
        long absVisibleStart = segLocalStart + (long)(visibleStartX / pxPerUs);
        long absVisibleEnd   = segLocalStart + (long)(visibleEndX   / pxPerUs);
        long firstT = ((absVisibleStart - gridOrigin) / step) * step + gridOrigin;
        firstT = Math.max(gridOrigin, firstT);
        long lastT = Math.min(segLocalEnd, absVisibleEnd);
        for (long t = firstT; t < lastT; t += step) {
            res.getPreview(t);
        }

        if (wf.dirty) {
            waveTex.draw(wf.pixmap, 0, 0);
            wf.dirty = false;
        }

        long bucketUs = wf.bucketDuration;
        int totalBuckets = wf.totalBuckets;
        if (totalBuckets <= 0) return;

        float startBucket = (float) segLocalStart / bucketUs;
        float endBucket = startBucket + (float) segDuration / bucketUs;

        ShaderProgram shader = getWaveShader();
        if (!shader.isCompiled()) return;

        batch.setShader(shader);
        shader.setUniformf("u_texWidth", wf.texWidth);
        shader.setUniformf("u_texHeight", wf.texHeight);
        shader.setUniformf("u_startBucket", startBucket);
        shader.setUniformf("u_endBucket", endBucket);
        shader.setUniformf("u_totalBuckets", totalBuckets);
        shader.setUniformf("u_color",
            Colors.ACCENT.r, Colors.ACCENT.g, Colors.ACCENT.b, Colors.ACCENT.a * parentAlpha);

        batch.draw(waveTex, getX(), getY(), getWidth(), getHeight());
        batch.setShader(null);
    }

    private static final String VERT =
        """
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec2 v_texCoord;
            void main() {
                v_texCoord = a_texCoord0;
                gl_Position = u_projTrans * a_position;
            }""";

    private static final String FRAG =
        """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec2 v_texCoord;
            uniform sampler2D u_texture;
            uniform float u_texWidth;
            uniform float u_texHeight;
            uniform float u_startBucket;
            uniform float u_endBucket;
            uniform float u_totalBuckets;
            uniform vec4 u_color;
            void main() {
                float bucket = u_startBucket + v_texCoord.x * (u_endBucket - u_startBucket);
                if (bucket < 0.0 || bucket >= u_totalBuckets) { discard; return; }
                float pixelIdx = floor(bucket);
                float u = mod(pixelIdx, u_texWidth) / u_texWidth;
                float v = floor(pixelIdx / u_texWidth) / u_texHeight;
                float amp = texture2D(u_texture, vec2(u, v)).r;
                float dist = abs(v_texCoord.y - 0.5) * 2.0;
                if (dist <= amp) {
                    gl_FragColor = u_color;
                } else {
                    discard;
                }
            }""";
}
