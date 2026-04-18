package com.lomekwi.cave.ui.editpanel.previewarea;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.pipeline.PipelineEvents;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.pipeline.image.ImgProd;
import com.lomekwi.cave.ui.Root;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责渲染和显示预览内容
 */
@NullMarked
public class PreviewArea extends Group {
    private final List<ImgProd> prods = new ArrayList<>();
    private final Project project;
    private float xOffset, yOffset;
    private float lastMouseX, lastMouseY;
    private boolean isDragging = false;
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
                isDragging = true;
                lastMouseX = x;
                lastMouseY = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (isDragging) {
                    float deltaX = x - lastMouseX;
                    float deltaY = y - lastMouseY;

                    xOffset += deltaX;
                    yOffset += deltaY;

                    lastMouseX = x;
                    lastMouseY = y;

                    updateAllImages();
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                isDragging = false;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                float zoomFactor = 1.1f;
                float oldScale = scale;

                scale *= Math.pow(zoomFactor, -amountY);

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
        for (ImgProd prod : prods) {
            prod.getImage().setPosition(xOffset, yOffset);
            prod.getImage().setScale(scale);
            prod.applyTransform();
        }
    }

    @Subscribe
    public void sink(ImgProd product) {
        prods.add(product);
        product.update();
        Image i = product.getImage();
        addActor(i);
        i.setPosition(xOffset, yOffset);
        i.setScale(scale);
        product.applyTransform();
    }

    @Subscribe
    public void clear(PipelineEvents.LastFrameEndEvent event) {
        clearChildren(false);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
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
