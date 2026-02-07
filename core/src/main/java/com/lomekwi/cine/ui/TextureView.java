package com.lomekwi.cine.ui;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.lomekwi.cine.output.OutputDispatcher;
import com.lomekwi.cine.output.Outputable;
import com.lomekwi.cine.output.Outputter;
import com.lomekwi.cine.pipeline.upload.TexProd;

import java.util.HashMap;
import java.util.Map;
//FIXME:当删除VideoClip时，不会删除对应的Image，从而会导致内存泄漏
public class TextureView extends Group implements Outputter {
    public TextureView(OutputDispatcher outputDispatcher) {
        outputDispatcher.addOutput(TexProd.class, this);
    }
    private final Map<TexProd, Image> textures= new HashMap<>();
    @Override
    public void output(Outputable outputable) {
        TexProd texture = (TexProd) outputable;

        Image img = textures.computeIfAbsent(texture, k -> {
            Image newImage = new Image(texture);
            addActor(newImage);
            return newImage;
        });

        img.setVisible(true);
    }

    @Override
    public void reset() {
        for (Image img : textures.values()) {
            img.setVisible(false);
        }
    }

}
