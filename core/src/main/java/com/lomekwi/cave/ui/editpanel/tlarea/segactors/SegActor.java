package com.lomekwi.cave.ui.editpanel.tlarea.segactors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cave.ui.Root;

public abstract class SegActor extends Actor {
    @Override
    public void draw(Batch batch, float parentAlpha) {
        Root.getInstance().getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), Color.SKY);
        Root.getInstance().getShapeDrawer().rectangle(getX(), getY(), getWidth(), getHeight(), Color.BLUE, 10);
    }
}
