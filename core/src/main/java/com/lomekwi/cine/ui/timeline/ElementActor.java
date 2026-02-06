package com.lomekwi.cine.ui.timeline;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cine.util.WhiteTexture;

public class ElementActor extends Actor {
    @Override
    public void draw (Batch batch, float parentAlpha) {
        batch.setColor(1,1,1,parentAlpha);
        batch.draw(WhiteTexture.getInstance(), getX(), getY(), getWidth(), getHeight());
        batch.setColor(1,1,1,1);
    }
}
