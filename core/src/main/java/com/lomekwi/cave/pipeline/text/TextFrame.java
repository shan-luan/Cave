package com.lomekwi.cave.pipeline.text;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.lomekwi.cave.pipeline.image.Transform;
import com.lomekwi.cave.pipeline.image.Transformable;

import java.nio.CharBuffer;

public class TextFrame implements Transformable {
    private CharBuffer text;
    private BitmapFont font;
    private final GlyphLayout layout=new GlyphLayout();
    private Transform transform;

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
        layout.setText(font, text);
        font.draw(batch, layout, transform.x, transform.y);
    }
}
