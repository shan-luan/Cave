package com.lomekwi.cave.ui.editpanel.previewarea;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.util.Vars;
import com.lomekwi.cave.pipeline.Sink;
import com.lomekwi.cave.pipeline.image.ImgProd;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责 FBO 的创建、渲染和资源管理
 */
public class TlPrevCont implements Sink<ImgProd> {
    private final FrameBuffer fbo;
    private final List<ImgProd> prods = new ArrayList<>();
    private final TextureRegionDrawable drawable;
    private final Batch batch;
    private final Project project;

    public TlPrevCont(Project project, int width, int height) {
        this.project = project;
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        TextureRegion region = new TextureRegion(fbo.getColorBufferTexture());
        region.flip(false, true);
        drawable = new TextureRegionDrawable(region);
        batch = new SpriteBatch();
        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, width, height));
        project.distributor.registerSink(ImgProd.class, this);
    }

    public FrameBuffer getFbo() {
        return fbo;
    }

    @Override
    public void sink(ImgProd product) {
        prods.add(product);
    }

    /** 渲染到 FBO */
    public void render() {
        fbo.begin();
        batch.begin();

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        for (ImgProd prod : prods) {
            prod.updateAndDraw(batch);
        }

        batch.end();
        fbo.end();

        prods.clear();
    }

    public void dispose() {
        fbo.dispose();
        batch.dispose();
        project.distributor.unregisterSink(ImgProd.class, this);
    }

    public TextureRegionDrawable getDrawable() {
        return drawable;
    }
}
