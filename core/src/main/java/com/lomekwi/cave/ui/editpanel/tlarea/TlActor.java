package com.lomekwi.cave.ui.editpanel.tlarea;

import static com.lomekwi.cave.util.Units.SECOND;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.ui.Root;

import space.earlygrey.shapedrawer.ShapeDrawer;

public class TlActor extends Actor {

    private final ShapeDrawer shapeDrawer;
    private final TextureRegion region;

    private final Timeline timeline;
    private final Playhead playhead;

    private long viewStartTime;
    private long viewDurationTime;

    public TlActor(Timeline timeline, Playhead playhead) {

        this.timeline = timeline;
        this.playhead = playhead;

        Pixmap white = new Pixmap(1,1, Pixmap.Format.RGBA8888);
        white.setColor(Color.WHITE);
        white.fill();

        region = new TextureRegion(new Texture(white));
        shapeDrawer = new ShapeDrawer(Root.getInstance().getStage().getBatch(), region);

        white.dispose();

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                playhead.setTime(xToAbsoluteTime(x));
            }
        });

        //for test
        viewStartTime =0;
        viewDurationTime =300*SECOND;
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        super.draw(batch, parentAlpha);

        shapeDrawer.filledRectangle(getX(), getY(), getWidth(), getHeight());

        float x = absoluteTimeToX(playhead.getTime());
        shapeDrawer.line(
            x,
            getY(),
            x,
            getY() + getHeight(),
            Color.RED,
            3
        );
    }

    public void dispose(){
        region.getTexture().dispose();
    }
    private float absoluteTimeToX(long time) {
        return getX()
            + (float)(time - viewStartTime)
            / viewDurationTime
            * getWidth();
    }
    private long xToAbsoluteTime(float x) {
        float ratio = (x - getX()) / getWidth();
        return viewStartTime + (long)(ratio * viewDurationTime);
    }
}
