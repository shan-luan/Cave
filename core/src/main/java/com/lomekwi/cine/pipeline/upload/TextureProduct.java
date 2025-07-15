package com.lomekwi.cine.pipeline.upload;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.lomekwi.cine.output.Outputable;

public class TextureProduct extends Texture implements Outputable {
    public TextureProduct (int width, int height, Pixmap.Format format) {
        super(width, height, format);
    }
}
