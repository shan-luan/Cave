package com.lomekwi.cave.pipeline.image;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Scaling;
import com.lomekwi.cave.pipeline.Frame;
import com.lomekwi.cave.timeline.Track;

import java.nio.ByteBuffer;

public class ImgFrame extends Frame implements Transformable {
    private Transform transform;
    private ByteBuffer pixels;
    private Texture texture;
    private Image image;
    public volatile boolean changed = true;

    public ImgFrame(Track track) {
        super(track);
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
        image = new Image(texture);
        image.setScaling(Scaling.stretch);
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

    public Image getImage() {
        return image;
    }

    /**
     * 将transform的偏移应用到Image上(叠加而不是覆盖)
     * transform.{x,y}为虚拟坐标空间（帧像素），按当前scale缩放到屏幕坐标。
     */
    public void applyTransform() {
        if (image != null && transform != null) {
            float s = image.getScaleX();
            image.setPosition(image.getX() + transform.x * s, image.getY() + transform.y * s);
            image.setSize(transform.width, transform.height);
        }
    }
}
