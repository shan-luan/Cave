package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.ui.editpanel.previewarea.TransFrameActor;

import java.nio.ByteBuffer;

public class ImgFrame extends Frame implements Transformable,Renderable {
    private Transform transform;
    private ByteBuffer pixels;
    private volatile boolean pixelsDirty;
    private Texture texture;
    private TransFrameActor actor;
    private int unpackRowLength;

    public ImgFrame(Track track) {
        super(track);
    }

    public ImgFrame(Track track, Source<?> source) {
        super(track, source);
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
        return texture.getWidth();
    }
    @Override
    public float getBaseHeight() {
        return texture.getHeight();
    }
    public ByteBuffer getPixels() {
        return pixels;
    }

    public void setPixels(ByteBuffer pixels) {
        this.pixels = pixels;
        pixelsDirty = true;
    }
    public Texture getTexture() {
        return texture;
    }
    public ImgFrame setTexture(Texture texture) {
        this.texture = texture;
        actor = new TransFrameActor(this);
        return this;
    }

    public int getUnpackRowLength() {
        return unpackRowLength;
    }

    public void setUnpackRowLength(int unpackRowLength) {
        this.unpackRowLength = unpackRowLength;
    }

    public void upload() {
        if (!pixelsDirty) return;
        pixelsDirty = false;
        if (pixels != null) {
            Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, unpackRowLength);
            texture.bind();
            Gdx.gl.glTexSubImage2D(
                GL20.GL_TEXTURE_2D,
                0,
                0,
                0,
                texture.getWidth(),
                texture.getHeight(),
                GL20.GL_RGBA,
                GL20.GL_UNSIGNED_BYTE,
                pixels
            );
            Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, 0);
        }
    }
    @Override
    public void close() {
        if (actor != null && actor.getParent() == null) {
            Gdx.app.postRunnable(this::close);
        } else {
            texture.dispose();
        }
    }

    public TransFrameActor getActor() {
        return actor;
    }

    @Override
    public void render(Batch batch) {
        upload();
        var t = getTransform();
        float baseW = getBaseWidth();
        float baseH = getBaseHeight();
        float scaleX = t.isFlipX() ? -1 : 1;
        float scaleY = t.isFlipY() ? -1 : 1;
        float w = baseW * t.getScaleX();
        float h = baseH * t.getScaleY();
        batch.draw(getTexture(), t.getX(), t.getY(), w/2, h/2, w, h, scaleX, scaleY, t.getRotation(), 0, 0, (int) baseW, (int) baseH, false, false);
    }
}
