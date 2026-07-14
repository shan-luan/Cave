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
    private String text;
    private BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private Transform transform;
    private TransFrameActor actor;
    private final Matrix4 tmpMatrix = new Matrix4();

    public TextFrame(Track track) {
        super(track);
    }

    public TextFrame(Track track, Source<?> source) {
        super(track, source);
    }

    public void setText(CharSequence text) {
        this.text = text.toString();
        if (font != null) {
            layout.setText(font, this.text);
        }
    }

    public void setFont(BitmapFont font) {
        this.font = font;
        if (text != null) {
            layout.setText(font, text);
        }
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
        return layout.width;
    }

    @Override
    public float getBaseHeight() {
        return layout.height;
    }

    @Override
    public void render(Batch batch) {
        if (font == null || text == null) return;
        var t = getTransform();
        float scaleX = t.flipX ? -1 : 1;
        float scaleY = t.flipY ? -1 : 1;
        float w = layout.width;
        float h = layout.height;

        Matrix4 saved = new Matrix4(batch.getTransformMatrix());
        float sx = scaleX * t.getScaleX();
        float sy = scaleY * t.getScaleY();
        tmpMatrix.set(saved);
        tmpMatrix.translate(t.x + w * sx / 2, t.y + h * sy / 2, 0);
        tmpMatrix.rotate(0, 0, 1, t.rotation);
        tmpMatrix.scale(sx, sy, 1);
        batch.setTransformMatrix(tmpMatrix);
        font.draw(batch, text, -w / 2, -font.getDescent());
        batch.setTransformMatrix(saved);
    }
}
