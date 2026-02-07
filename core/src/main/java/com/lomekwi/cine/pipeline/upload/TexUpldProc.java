package com.lomekwi.cine.pipeline.upload;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.pipeline.decode.PixProd;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class TexUpldProc implements Processor {
    public static final TexUpldProc INSTANCE = new TexUpldProc();
    private final Map<PixProd, TexProd> textures = new HashMap<>();
    @Override
    public void process(Product pixel, Queue<Product> collector) {
        PixProd pixProd = (PixProd) pixel;
        TexProd texture = textures.computeIfAbsent(pixProd, k -> new TexProd(pixProd.getWidth(), pixProd.getHeight(),Pixmap.Format.RGBA8888));
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, texture.getTextureObjectHandle());
        Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, pixProd.getWidth(), pixProd.getHeight(), GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixProd.getPixels());
        collector.add(texture);
    }
    public void remove(PixProd pixProd) {
        textures.get(pixProd).dispose();
        textures.remove(pixProd);
    }
}
