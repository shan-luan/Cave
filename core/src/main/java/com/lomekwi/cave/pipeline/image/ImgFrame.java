package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.ui.editpanel.previewarea.ImgFrameActor;

import java.nio.ByteBuffer;

public class ImgFrame extends Frame implements Transformable {
    private Transform transform;
    private ByteBuffer pixels;
    private Texture texture;
    private ImgFrameActor actor;
    public volatile boolean changed = true;

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
    public ByteBuffer getPixels() {
        return pixels;
    }

    public ImgFrame setPixels(ByteBuffer pixels) {
        this.pixels = pixels;
        return this;
    }
    public Texture getTexture() {
        return texture;
    }
    public ImgFrame setTexture(Texture texture) {
        this.texture = texture;
        actor = new ImgFrameActor(this);
        return this;
    }

    public void update() {
        if(changed && pixels != null) {
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
            changed = false;
        }
    }
    @Override
    public void close() {
        texture.dispose();
    }

    public ImgFrameActor getActor() {
        return actor;
    }

    /**
     * 将transform应用到Image上
     * 在内层Group内部使用局部坐标
     */
    public void applyTransform() {
        if (actor != null && transform != null) {
            actor.setPosition(transform.x, transform.y);
            actor.setSize(transform.width, transform.height);
            
            float pivotX = transform.width / 2 + transform.pivotX * transform.width / 2;
            float pivotY = transform.height / 2 + transform.pivotY * transform.height / 2;
            actor.setOrigin(pivotX, pivotY);
            
            actor.setRotation(transform.rotation);
        }
    }
}
