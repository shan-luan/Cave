package com.lomekwi.cine.pipeline.upload;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.lomekwi.cine.output.Outputable;

public class TexProd extends Texture implements Outputable {
    public TexProd(int width, int height, Pixmap.Format format) {
        super(width, height, format);
    }
}
