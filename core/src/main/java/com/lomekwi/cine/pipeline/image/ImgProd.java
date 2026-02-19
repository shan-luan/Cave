package com.lomekwi.cine.pipeline.image;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.lomekwi.cine.pipeline.Product;

import java.nio.ByteBuffer;

public class ImgProd implements Product, Transformable<ImgProd>{
    private Transform transform;
    private ByteBuffer pixels;
    private Texture texture;
    public boolean changed = true;
    @Override
    public Transform getTransform() {
        return transform;
    }
    @Override
    public ImgProd setTransform(Transform transform) {
        this.transform = transform;
        return this;
    }
    public ByteBuffer getPixels() {
        return pixels;
    }

    public ImgProd setPixels(ByteBuffer pixels) {
        this.pixels = pixels;
        return this;
    }
    public Texture getTexture() {
        return texture;
    }
    public ImgProd setTexture(Texture texture) {
        this.texture = texture;
        return this;
    }
    public ImgProd updateAndDraw(Batch batch){
        if(changed) {
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
        batch.draw(
            texture,
            transform.x,
            transform.y,
            transform.width/2,
            transform.height/2,
            transform.width,
            transform.height,
            1,
            1,
            transform.rotation,
            0,0,
            texture.getWidth(),
            texture.getHeight(),
            false,
            false
        );
        return this;
    }
    @Override
    public void close() {
        texture.dispose();
    }
}
