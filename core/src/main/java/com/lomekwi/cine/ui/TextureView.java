package com.lomekwi.cine.ui;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.lomekwi.cine.output.OutputDispatcher;
import com.lomekwi.cine.output.Outputable;
import com.lomekwi.cine.output.Outputter;
import com.lomekwi.cine.pipeline.upload.TextureProduct;

import java.util.HashMap;
import java.util.Map;

public class TextureView extends Group implements Outputter {
    public TextureView(OutputDispatcher outputDispatcher) {
        outputDispatcher.addOutput(TextureProduct.class, this);
    }
    private final Map<TextureProduct, Image> textures= new HashMap<>();
    @Override
    public void output(Outputable outputable) {
        TextureProduct texture = (TextureProduct) outputable;

        Image img = textures.computeIfAbsent(texture, k -> {
            Image newImage = new Image(texture);
            addActor(newImage);
            return newImage;
        });

        img.setVisible(true);
        setDebug(true);
    }

    @Override
    public void reset() {
        for (Image img : textures.values()) {
            img.setVisible(false);
        }
    }

}
