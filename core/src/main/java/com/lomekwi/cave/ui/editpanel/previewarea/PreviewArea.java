package com.lomekwi.cave.ui.editpanel.previewarea;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.pipeline.GapFrame;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.pipeline.image.ImgFrame;
import com.lomekwi.cave.timeline.Track;
import com.lomekwi.cave.timeline.playback.SeekEvent;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.util.Units;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责渲染和显示预览内容
 */
@NullMarked
public class PreviewArea extends Group {
    private final Project project;
    //此列表仅应在主线程读取.
    private final List<@Nullable ImgFrame> frames = new ArrayList<>();
    private float xOffset, yOffset;
    private float lastMouseX, lastMouseY;
    private float scale = 1.0f;
    private float userZoom = 1.0f;
    private float refViewportArea = -1f;
    private static final float MIN_SCALE = 0.07f;
    private static final float MAX_SCALE = 30.0f;
    private float lastWidth = 0;
    private float lastHeight = 0;

    private void recalcScale() {
        float vr = refViewportArea > 0
            ? (float) Math.sqrt((double) getWidth() * getHeight() / refViewportArea)
            : 1f;
        scale = userZoom * vr;
    }

    public PreviewArea(Project project) {
        this.project = project;
        project.projEventBus.register(this);
        setupDragListener();
    }

    private void setupDragListener() {
        addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if(x<0||x>getWidth()||y<0||y>getHeight())return false;
                lastMouseX = x;
                lastMouseY = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                float deltaX = x - lastMouseX;
                float deltaY = y - lastMouseY;

                xOffset += deltaX;
                yOffset += deltaY;

                lastMouseX = x;
                lastMouseY = y;

                updateAllImages();
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                float zoomFactor = 1.1f;
                float oldScale = scale;

                userZoom *= (float) Math.pow(zoomFactor, -amountY);
                userZoom = Math.max(MIN_SCALE, Math.min(MAX_SCALE, userZoom));
                recalcScale();

                xOffset = x - (x - xOffset) * (scale / oldScale);
                yOffset = y - (y - yOffset) * (scale / oldScale);

                updateAllImages();
                return true;
            }
        });

        addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                if (pointer == -1) {
                    App.root.getStage().setScrollFocus(PreviewArea.this);
                }
            }
        });
    }

    private void updateAllImages() {
        for (ImgFrame frame : frames) {
            if(frame!=null) {
                frame.getImage().setPosition(xOffset, yOffset);
                frame.getImage().setScale(scale);
                frame.applyTransform();
            }
        }
    }

    @Subscribe
    public void sink(ImgFrame frame) {
        Track track = frame.track;
        track.getWorker().getSinkPhaser().register();
        //因为postRunnable内部是线程安全队列，保证了上传纹理happens-before更新pixels.
        Gdx.app.postRunnable(()-> {
            setFrame(frame);
            frame.update();
            Image i = frame.getImage();
            addActor(i);
            i.setPosition(xOffset, yOffset);
            i.setScale(scale);
            frame.applyTransform();
            track.getWorker().getSinkPhaser().arriveAndDeregister();
        });
    }
    private void setFrame(ImgFrame frame){
        int idx = frame.track.index;
        while (idx >= frames.size()) {
            frames.add(null);
        }
        var legacy=frames.set(frame.track.index, frame);
        if (legacy != null){
            removeActor(legacy.getImage());
        }
    }
//TODO:减少对象分配开销
    public void clearFrames(int idx) {
        Gdx.app.postRunnable(() -> {
            // 边界检查
            if (idx < 0 || idx >= frames.size()) {
                return;
            }
            ImgFrame frame = frames.get(idx);
            if (frame == null) {
                return;
            }
            Image image = frame.getImage();
            if (image == null) {
                return;
            }
            // 所有条件满足，执行清理
            frames.set(idx,null);
            removeActor(image);
        });
    }

    @Subscribe
    public void clear(GapFrame event) {
        clearFrames(event.track.index);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        int i = 0;
        for(ImgFrame frame : frames){
            if(frame!=null){
                frame.getImage().setZIndex(getChildren().size-1-i);
                i++;
            }
        }
    }

    private static final Color AXIS_COLOR = new Color(0.5f, 0.5f, 0.5f, 0.6f);
    private static final int TICK_PIXEL_TARGET = 80;

    @Override
    public void draw(Batch batch, float parentAlpha) {
        App.root.getShapeDrawer().filledRectangle(getX(), getY(), getWidth(), getHeight(), Color.BLACK);
        super.draw(batch, parentAlpha);
        drawAxes();
    }

    private void drawAxes() {
        var drawer = App.root.getShapeDrawer();
        float ox = getX() + xOffset;
        float oy = getY() + yOffset;
        float x0 = getX();
        float x1 = getX() + getWidth();
        float y0 = getY();
        float y1 = getY() + getHeight();

        drawer.line(x0, oy, x1, oy, AXIS_COLOR);
        drawer.line(ox, y0, ox, y1, AXIS_COLOR);

        float tickHalf = 4f;
        float interval = Units.niceInterval(TICK_PIXEL_TARGET / scale);

        float startV = (x0 - ox) / scale;
        float endV = (x1 - ox) / scale;
        double first = Math.ceil(startV / interval) * interval;
        for (double v = first; v <= endV; v += interval) {
            if (Math.abs(v) < interval * 0.01f) continue;
            float sx = ox + (float) v * scale;
            drawer.line(sx, oy - tickHalf, sx, oy + tickHalf, AXIS_COLOR);
        }

        startV = (y0 - oy) / scale;
        endV = (y1 - oy) / scale;
        first = Math.ceil(startV / interval) * interval;
        for (double v = first; v <= endV; v += interval) {
            if (Math.abs(v) < interval * 0.01f) continue;
            float sy = oy + (float) v * scale;
            drawer.line(ox - tickHalf, sy, ox + tickHalf, sy, AXIS_COLOR);
        }
    }

    @Override
    public void sizeChanged() {
        if (lastWidth > 0 && lastHeight > 0) {
            if (refViewportArea < 0) {
                refViewportArea = lastWidth * lastHeight;
            }
            recalcScale();
            updateAllImages();
        }
        lastWidth = getWidth();
        lastHeight = getHeight();
    }

    public void dispose() {
        project.projEventBus.unregister(this);
    }
}
