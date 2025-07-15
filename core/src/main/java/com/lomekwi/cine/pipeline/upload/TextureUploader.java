package com.lomekwi.cine.pipeline.upload;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.pipeline.decode.Pixels;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class TextureUploader implements Processor {
    public static final TextureUploader INSTANCE = new TextureUploader();
    private final Map<Pixels,TextureProduct> textures = new HashMap<>();
    @Override
    public void process(Product product, Queue<Product> collector) {
        Pixels pixels = (Pixels) product;
        TextureProduct texture = textures.computeIfAbsent(pixels,k -> new TextureProduct(pixels.getWidth(),pixels.getHeight(),Pixmap.Format.RGBA8888));
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, texture.getTextureObjectHandle());
        Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, pixels.getWidth(), pixels.getHeight(), GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels.getPixels());
        collector.add(texture);
    }
    public void remove(Pixels pixels) {
        textures.get(pixels).dispose();
        textures.remove(pixels);
    }
}
