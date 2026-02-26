package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.lomekwi.cave.ui.Root;

import space.earlygrey.shapedrawer.ShapeDrawer;

public class TlActor extends Actor {
    private final ShapeDrawer shapeDrawer;
    public TlActor(){
        Pixmap white = new Pixmap(1,1, Pixmap.Format.RGBA8888);
        white.setColor(Color.WHITE);
        white.fill();
        shapeDrawer = new ShapeDrawer(Root.getInstance().getStage().getBatch(),new TextureRegion(new Texture(white)));
        white.dispose();
    }
    @Override
    public void draw(Batch batch, float parentAlpha){
        super.draw(batch, parentAlpha);
        shapeDrawer.filledRectangle(getX(), getY(), getWidth(), getHeight());
    }
}
