package com.lomekwi.cave.ui.editpanel.previewarea;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.pipeline.GapFrame;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.pipeline.image.ImgFrame;
import com.lomekwi.cave.timeline.Segment;
import com.lomekwi.cave.timeline.SegmentSelectedEvent;
import com.lomekwi.cave.timeline.Track;
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
    private final Group canvas = new Group();
    //此列表仅应在主线程读取.
    private final List<@Nullable ImgFrame> frames = new ArrayList<>();
    private float xOffset, yOffset;
    private float scale = 1.0f;
    private float zoom = 1.0f;
    private float refViewportArea = -1f;
    private static final float MIN_SCALE = 0.07f;
    private static final float MAX_SCALE = 30.0f;
    private float lastWidth = 0;
    private float lastHeight = 0;
    private static final float MOVE_SPEED = 1000f;
    private final com.badlogic.gdx.math.Vector2 screenPos = new com.badlogic.gdx.math.Vector2();

    private void recalcScale() {
        float vr = refViewportArea > 0
            ? (float) Math.sqrt((double) getWidth() * getHeight() / refViewportArea)
            : 1f;
        scale = zoom * vr;
    }

    public PreviewArea(Project project) {
        this.project = project;
        project.projEventBus.register(this);
        addActor(canvas);
        setupDragListener();
    }

    private void setupDragListener() {
        addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if(x<0||x>getWidth()||y<0||y>getHeight())return false;
                if (button == 0 && event.getTarget() == PreviewArea.this) {
                    var editPanel = App.root.getFrontendEditPanel();
                    if (editPanel != null) {
                        var tlGroup = editPanel.getTlGroup();
                        tlGroup.clearSelection();
                    }
                }
                return true;
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                var editPanel = App.root.getFrontendEditPanel();
                if (editPanel != null) {
                    var tlGroup = editPanel.getTlGroup();
                    tlGroup.clearSelection();
                }
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                float zoomFactor = 1.1f;
                float oldScale = scale;

                zoom *= (float) Math.pow(zoomFactor, -amountY);
                zoom = Math.max(MIN_SCALE, Math.min(MAX_SCALE, zoom));
                recalcScale();

                screenPos.set(event.getStageX(), event.getStageY());
                stageToLocalCoordinates(screenPos);
                xOffset = screenPos.x - (screenPos.x - xOffset) * (scale / oldScale);
                yOffset = screenPos.y - (screenPos.y - yOffset) * (scale / oldScale);

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
            @Override
            public void exit(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor toActor) {
                if (pointer == -1) {
                    var stage = App.root.getStage();
                    if (stage.getScrollFocus() == PreviewArea.this) {
                        stage.setScrollFocus(null);
                    }
                }
            }
        });
    }

    private void updateAllImages() {
        canvas.setPosition(xOffset, yOffset);
        canvas.setScale(scale);
        for (ImgFrame frame : frames) {
            if(frame!=null) {
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
            ImgFrameActor i = frame.getActor();
            canvas.addActor(i);
            canvas.setPosition(xOffset, yOffset);
            canvas.setScale(scale);
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
            canvas.removeActor(legacy.getActor());
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
            ImgFrameActor actor = frame.getActor();
            if (actor == null) {
                return;
            }
            // 所有条件满足，执行清理
            frames.set(idx,null);
            canvas.removeActor(actor);
        });
    }

    @Subscribe
    public void clear(GapFrame event) {
        clearFrames(event.track.index);
    }

    @Subscribe
    public void onSegmentSelected(SegmentSelectedEvent event) {
        for (ImgFrame frame : frames) {
            if (frame != null && frame.getActor() != null) {
                Segment segment = frame.getSource() != null ? frame.getSource().getSegment() : null;
                boolean selected = segment != null && segment.isSelected();
                frame.getActor().setSelected(selected);
            }
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        var stage = getStage();
        if (stage != null) {
            screenPos.set(Gdx.input.getX(), Gdx.input.getY());
            stage.screenToStageCoordinates(screenPos);
            stageToLocalCoordinates(screenPos);
            float lx = screenPos.x;
            float ly = screenPos.y;
            if (lx >= 0 && lx <= getWidth() && ly >= 0 && ly <= getHeight()) {
                float speed = MOVE_SPEED * delta / scale;
                if (Gdx.input.isKeyPressed(Input.Keys.W)) yOffset -= speed;
                if (Gdx.input.isKeyPressed(Input.Keys.S)) yOffset += speed;
                if (Gdx.input.isKeyPressed(Input.Keys.A)) xOffset += speed;
                if (Gdx.input.isKeyPressed(Input.Keys.D)) xOffset -= speed;
                updateAllImages();
            }
        }
        canvas.setZIndex(0);
        int i = 0;
        for(ImgFrame frame : frames){
            if(frame!=null && frame.getActor() != null && frame.getActor().getParent() == canvas){
                frame.getActor().setZIndex(canvas.getChildren().size-1-i);
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
        float ox = getX() + canvas.getX();
        float oy = getY() + canvas.getY();
        float x0 = getX();
        float x1 = getX() + getWidth();
        float y0 = getY();
        float y1 = getY() + getHeight();

        drawer.line(x0, oy, x1, oy, AXIS_COLOR);
        drawer.line(ox, y0, ox, y1, AXIS_COLOR);

        float tickHalf = 4f;
        float interval = Units.niceInterval(TICK_PIXEL_TARGET / canvas.getScaleX());

        float startV = (x0 - ox) / canvas.getScaleX();
        float endV = (x1 - ox) / canvas.getScaleX();
        double first = Math.ceil(startV / interval) * interval;
        for (double v = first; v <= endV; v += interval) {
            if (Math.abs(v) < interval * 0.01f) continue;
            float sx = ox + (float) v * canvas.getScaleX();
            drawer.line(sx, oy - tickHalf, sx, oy + tickHalf, AXIS_COLOR);
        }

        startV = (y0 - oy) / canvas.getScaleX();
        endV = (y1 - oy) / canvas.getScaleX();
        first = Math.ceil(startV / interval) * interval;
        for (double v = first; v <= endV; v += interval) {
            if (Math.abs(v) < interval * 0.01f) continue;
            float sy = oy + (float) v * canvas.getScaleX();
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

    public void resetView() {
        zoom = 1.0f;
        xOffset = 0;
        yOffset = 0;
        recalcScale();
        updateAllImages();
    }

    public void dispose() {
        project.projEventBus.unregister(this);
    }
}
