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
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.google.common.collect.Range;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.pipeline.Source;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.project.ProjectEvents;
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
    private final Project project;

    private long viewStartTime;
    private long viewDurationTime;

    private float trackHeight;
    private float trackYShift;

    private final Color black = new Color(Color.BLACK).add(0,0,0,-0.5f);

    public TlActor(Project project) {
        this.project = project;
        this.timeline = project.timeline;
        this.playhead = project.playhead;

        project.projEventBus.register(this);

        final Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        white.setColor(Color.WHITE);
        white.fill();

        this.region = new TextureRegion(new Texture(white));
        this.shapeDrawer = new ShapeDrawer(Root.getInstance().getStage().getBatch(), region);
        white.dispose();

        this.viewStartTime = 0;
        this.viewDurationTime = Math.max(project.timeline.getLength(), 30 * SECOND);
        this.trackHeight = 80;

        addListener(new InputListener() {
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                final Input ip = Gdx.input;

                if (ip.isKeyPressed(CONTROL_LEFT) && ip.isKeyPressed(SHIFT_LEFT)) {
                    trackHeight = Math.max(trackHeight + amountY * 10, 10);

                } else if (ip.isKeyPressed(CONTROL_LEFT)) {
                    final float ratio = x / getWidth();
                    final long oldDuration = viewDurationTime;

                    final float scaleFactor = 1f + amountY * 0.1f;
                    if (scaleFactor <= 0f) return true;

                    long newDuration = (long) (oldDuration * scaleFactor);
                    if (newDuration <= SECOND) newDuration = SECOND;

                    final long anchorTime = viewStartTime + (long) (ratio * oldDuration);

                    viewDurationTime = newDuration;
                    viewStartTime = Math.max(anchorTime - (long) (ratio * newDuration), 0);

                } else if (ip.isKeyPressed(SHIFT_LEFT)) {
                    viewStartTime = Math.max(viewStartTime + (long) (amountY * SECOND), 0);

                } else {
                    trackYShift += amountY * 10;
                }
                return true;
            }

            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == SPACE) {
                    project.playhead.setPlaying(!project.playhead.isPlaying());
                }
                return true;
            }
        });

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                final long t = xToAbsoluteTime(x);
                project.playhead.setTime(Math.max(t, 0));
            }
        });

        addListener(event -> {
            if (event instanceof FocusListener.FocusEvent) {
                final FocusListener.FocusEvent e = (FocusListener.FocusEvent) event;
                if (Root.getInstance().getFrontendProject() == project && !e.isFocused()) {
                    e.cancel();
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);

        drawBackground();
        drawSplitters();
        drawTrackContents();
        drawPlayhead();
    }

    private void drawBackground() {
        shapeDrawer.filledRectangle(0, 0, getWidth(), getHeight(), Color.DARK_GRAY);

        final float startX = absoluteTimeToX(0);
        final float endX = absoluteTimeToX(timeline.getLength());

        shapeDrawer.filledRectangle(startX, 0, endX - startX, getHeight(), Color.GRAY);
    }
    private void drawSplitters() {
        float offset = ((trackYShift % trackHeight) + trackHeight) % trackHeight;
        float startY = getHeight() + offset;

        for (float y = startY; y > -trackHeight; y -= trackHeight) {
            shapeDrawer.line(0, y, getWidth(), y, black);
        }
    }

    private void drawTrackContents() {
        for (int i = 0; i < timeline.getTracks().size(); i++) {
            final Track track = timeline.getTracks().get(i);

            for (final Map.Entry<Range<Long>, Source<?>> entry : track.getSources().asMapOfRanges().entrySet()) {

                final float sx = absoluteTimeToX(entry.getKey().lowerEndpoint());
                final float ex = absoluteTimeToX(entry.getKey().upperEndpoint());

                final float baseY = getHeight() - trackHeight;
                final float y = baseY - i * trackHeight + trackYShift;

                shapeDrawer.filledRectangle(sx, y, ex - sx, trackHeight, Color.WHITE);
            }
        }
    }

    private void drawPlayhead() {
        final float x = absoluteTimeToX(playhead.getTime());

        shapeDrawer.filledTriangle(
            x - 10, getHeight(),
            x + 10, getHeight(),
            x, getHeight() - 20,
            Color.RED
        );

        shapeDrawer.line(x, 0, x, getHeight(), Color.RED, 3);
    }

    public void dispose() {
        region.getTexture().dispose();
        project.projEventBus.unregister(this);
    }

    private float absoluteTimeToX(long time) {
        return (float) (time - viewStartTime) / viewDurationTime * getWidth();
    }

    private long xToAbsoluteTime(float x) {
        final float ratio = x / getWidth();
        return viewStartTime + (long) (ratio * viewDurationTime);
    }

    @Subscribe
    public void onProjectFronted(ProjectEvents.ProjectFrontedEvent e) {
        Root.getInstance().getStage().setScrollFocus(this);
        Root.getInstance().getStage().setKeyboardFocus(this);
    }
}
