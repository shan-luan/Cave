package com.lomekwi.cine.pipeline.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.lomekwi.cine.pipeline.Processor;
import com.lomekwi.cine.pipeline.Product;

public class PixmapProduct extends Pixmap implements Product {
    public PixmapProduct(int width, int height, Format format) {
        super(width, height, format);
    }

    @Override
    public Processor getNextProcessor() {
        return null;
    }
}
