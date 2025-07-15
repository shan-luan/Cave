package com.lomekwi.cine.ui;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.lomekwi.cine.output.Outputable;
import com.lomekwi.cine.output.Outputter;
import com.lomekwi.cine.pipeline.Product;
import com.lomekwi.cine.pipeline.render.PixmapProduct;
import com.lomekwi.cine.util.Pair;

import java.util.HashMap;

public class PixmapOutput extends Group implements Outputter {
    private final HashMap<Product, Pair<Texture,Image>> images = new HashMap<>();
    @Override
    public void output(Outputable outputable) {
        PixmapProduct pixmap = (PixmapProduct) outputable;

    }
}
