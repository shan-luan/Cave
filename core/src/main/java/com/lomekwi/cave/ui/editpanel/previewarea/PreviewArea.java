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
import com.lomekwi.cave.ui.Root;
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
    private static final float MIN_SCALE = 0.07f;
    private static final float MAX_SCALE = 30.0f;
    private float lastWidth = 0;
    private float lastHeight = 0;

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

                scale *= (float) Math.pow(zoomFactor, -amountY);

                scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));

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
                    Root.getInstance().getStage().setScrollFocus(PreviewArea.this);
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
        Track track = project.timeline.getTracks().get(frame.trackIndex);
        track.getWorker().getSinkPhaser().register();
        //因为postRunnable内部是线程安全队列，保证了上传纹理happens-before更新pixels.
        Gdx.app.postRunnable(()-> {
            addFrame(frame);
            frame.update();
            Image i = frame.getImage();
            addActor(i);
            i.setPosition(xOffset, yOffset);
            i.setScale(scale);
            frame.applyTransform();
            track.getWorker().getSinkPhaser().arriveAndDeregister();
        });
    }
    private void addFrame(ImgFrame frame){
        int idx = frame.trackIndex;
        while (idx >= frames.size()) {
            frames.add(null);
        }
        frames.set(frame.trackIndex, frame);
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
        clearFrames(event.trackIndex);
    }
    @Subscribe
    public void clear(SeekEvent event){
        for(int i = 0; i < frames.size(); i++){
            clearFrames(i);
        }
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

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Root.getInstance().getShapeDrawer().filledRectangle(getX(),getY(),getWidth(),getHeight(), Color.BLACK);
        super.draw(batch, parentAlpha);
    }

    @Override
    public void sizeChanged() {
        if (lastWidth > 0 && lastHeight > 0) {
            float widthDelta = getWidth() - lastWidth;
            float heightDelta = getHeight() - lastHeight;

            xOffset += widthDelta;
            yOffset += heightDelta;

            updateAllImages();
        }

        lastWidth = getWidth();
        lastHeight = getHeight();
    }

    public void dispose() {
        project.projEventBus.unregister(this);
    }
}
