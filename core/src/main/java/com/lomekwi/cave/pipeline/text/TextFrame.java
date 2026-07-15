package com.lomekwi.cave.pipeline.text;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Matrix4;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.pipeline.image.Transform;
import com.lomekwi.cave.pipeline.image.Transformable;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.ui.editpanel.previewarea.TransFrameActor;

public class TextFrame extends Frame implements Transformable {
    private volatile String text;
    private volatile BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private Transform transform;
    private TransFrameActor actor;
    private final Matrix4 tmpMatrix = new Matrix4();
    private volatile boolean glyphsMissing;
    private volatile float cachedWidth;
    private volatile float cachedHeight;
    private volatile int version;
    private int layoutVersion;

    public TextFrame(Track track) {
        super(track);
    }

    public TextFrame(Track track, Source<?> source) {
        super(track, source);
    }

    public void setText(CharSequence text) {
        this.text = text.toString();
        version++;
    }

    public void setFont(BitmapFont font) {
        this.font = font;
        version++;
    }

    public void initActor() {
        actor = new TransFrameActor(this);
    }

    public TransFrameActor getActor() {
        return actor;
    }

    @Override
    public Transform getTransform() {
        return transform;
    }

    @Override
    public void setTransform(Transform transform) {
        this.transform = transform;
    }

    @Override
    public float getBaseWidth() {
        return cachedWidth;
    }

    @Override
    public float getBaseHeight() {
        return cachedHeight;
    }

    @Override
    public void render(Batch batch) {
        if (version != layoutVersion) {
            rebuildLayout();
            layoutVersion = version;
        }
        if (font == null || text == null || glyphsMissing) return;
        var t = getTransform();
        float scaleX = t.flipX ? -1 : 1;
        float scaleY = t.flipY ? -1 : 1;
        float w = cachedWidth;
        float h = cachedHeight;

        Matrix4 saved = new Matrix4(batch.getTransformMatrix());
        float sx = scaleX * t.getScaleX();
        float sy = scaleY * t.getScaleY();
        tmpMatrix.set(saved);
        tmpMatrix.translate(t.x + w * sx / 2, t.y + h * sy / 2, 0);
        tmpMatrix.rotate(0, 0, 1, t.rotation);
        tmpMatrix.scale(sx, sy, 1);
        batch.setTransformMatrix(tmpMatrix);
        try {
            font.draw(batch, layout, -w / 2, -font.getDescent());
        } catch (NullPointerException e) {
            glyphsMissing = true;
        }
        batch.setTransformMatrix(saved);
    }

    private void rebuildLayout() {
        String t = text;
        BitmapFont f = font;
        if (f == null || t == null) {
            glyphsMissing = true;
            cachedWidth = 0;
            cachedHeight = 0;
            return;
        }
        for (int i = 0; i < t.length(); i++) {
            if (f.getData().getGlyph(t.charAt(i)) == null) {
                glyphsMissing = true;
                cachedWidth = 0;
                cachedHeight = 0;
                return;
            }
        }
        layout.setText(f, t);
        glyphsMissing = false;
        cachedWidth = layout.width;
        cachedHeight = layout.height;
    }
}
