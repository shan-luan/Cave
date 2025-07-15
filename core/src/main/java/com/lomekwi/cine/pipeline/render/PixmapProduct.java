package com.lomekwi.cine.pipeline.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.lomekwi.cine.content.VideoClip;
import com.lomekwi.cine.output.Outputable;
import com.lomekwi.cine.pipeline.Product;

public class PixmapProduct extends Pixmap implements Outputable {
    private final VideoClip source;
    public PixmapProduct(int width, int height, Format format, VideoClip source) {
        super(width, height, format);
        this.source = source;
    }

    @Override
    public Product getSource() {
        return source;
    }

}
