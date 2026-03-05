package com.lomekwi.cave.ui.editpanel.tlarea;

import static com.lomekwi.cave.util.Units.SECOND;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.google.common.collect.Range;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.timeline.Timeline;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.playback.Playhead;
import com.lomekwi.cave.ui.Root;

import java.util.Map;

import space.earlygrey.shapedrawer.ShapeDrawer;

import static com.badlogic.gdx.Input.Keys.*;

public class TlActor extends Actor {

    private final ShapeDrawer shapeDrawer;
    private final TextureRegion region;

    private final Timeline timeline;
    private final Playhead playhead;

    private long viewStartTime;
    private long viewDurationTime;

    private float trackHeight;
    private float trackYShift;

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
                playhead.setTime(xToAbsoluteTime(getX()+x));
            }
        });

        //for test
        viewStartTime =-300*SECOND;
        viewDurationTime =900*SECOND;
        trackHeight = 50;

        Root.getInstance().getStage().setScrollFocus(this);
        addListener(new InputListener(){
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                final Input ip = Gdx.input;
                if(ip.isKeyPressed(CONTROL_LEFT)&&ip.isKeyPressed(SHIFT_LEFT)){
                    trackHeight = Math.max(trackHeight+amountY*10,10);
                }else if(ip.isKeyPressed(CONTROL_LEFT)){
                    float mouseStageX = getX() + x;
                    float ratio = (mouseStageX - getX()) / getWidth();
                    long oldDuration = viewDurationTime;
                    // 缩放因子
                    float scaleFactor = 1f + amountY * 0.1f;
                    if (scaleFactor <= 0f) return true;
                    long newDuration = (long)(oldDuration * scaleFactor);
                    if (newDuration <= SECOND) newDuration = SECOND;
                    long anchorTime = viewStartTime + (long)(ratio * oldDuration);
                    viewDurationTime = newDuration;
                    viewStartTime = anchorTime - (long)(ratio * newDuration);
                } else if (ip.isKeyPressed(SHIFT_LEFT)) {
                    viewStartTime += (long) (amountY*SECOND);
                }else {
                    trackYShift += amountY*10;
                }
                return true;
            }
        });
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        super.draw(batch, parentAlpha);

        shapeDrawer.filledRectangle(getX(), getY(), getWidth(), getHeight(),Color.DARK_GRAY);
        float startX = absoluteTimeToX(0);
        float endX = absoluteTimeToX(timeline.getLength());
        shapeDrawer.filledRectangle(startX, getY(), endX - startX, getHeight(), Color.GRAY);

        //绘制片段
        for(int i=0;i<timeline.getTracks().size();i++){
            Track track = timeline.getTracks().get(i);
            for(Map.Entry<Range<Long>, Source<?>> entry : track.getSources().asMapOfRanges().entrySet()){
                float sx = absoluteTimeToX(entry.getKey().lowerEndpoint());
                shapeDrawer.rectangle(sx,getY()+getHeight()+trackYShift-i*trackHeight,absoluteTimeToX(entry.getKey().upperEndpoint())-sx,-trackHeight);
            }
        }
        //绘制播放指针
        float x = absoluteTimeToX(playhead.getTime());
        shapeDrawer.filledTriangle(x-10,getY()+getHeight(),x+10,getY()+getHeight(),x,getY()+getHeight()-20,Color.RED);
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
