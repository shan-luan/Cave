package com.lomekwi.cave.ui.editpanel.previewarea;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.google.common.eventbus.Subscribe;
import com.lomekwi.cave.pipeline.PipelineEvents;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.pipeline.image.ImgProd;

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
    private float xOffset,yOffset;

    public PreviewArea(Project project) {
        this.project = project;
        project.projEventBus.register(this);
        xOffset=getX();
        yOffset=getY();
    }

    @Subscribe
    public void sink(ImgProd product) {
        product.update();
        Image i = product.getImage();
        addActor(i);
        i.setPosition(i.getX() + xOffset, i.getY() + yOffset);
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
        super.draw(batch, parentAlpha);
    }

    public void dispose() {
        project.projEventBus.unregister(this);
    }
}
