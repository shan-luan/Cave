package com.lomekwi.cine.ui.editpanel.previewarea;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.widget.VisImage;
import com.lomekwi.cine.GlobalVars;
import com.lomekwi.cine.pipeline.Sink;
import com.lomekwi.cine.pipeline.image.ImgProd;
import com.lomekwi.cine.project.Project;

import java.util.ArrayList;
import java.util.List;

public class PreviewArea extends VisImage implements Sink<ImgProd> {
    private final FrameBuffer fbo;
    private final TextureRegionDrawable drawable;
    private final List<ImgProd> prods = new ArrayList<>();

    public PreviewArea(int width, int height) {
        // 创建 FBO
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);

        // 创建可绘制的 TextureRegion
        TextureRegion region = new TextureRegion(fbo.getColorBufferTexture());
        region.flip(false, true); // LibGDX FBO 需要上下翻转
        drawable = new TextureRegionDrawable(region);

        //Image的初始化
        setDrawable(drawable);
        this.setScaling(Scaling.stretch);
        this.setAlign( Align.center);
        setSize(width, height);

        GlobalVars.getProject().distributor.registerSink(ImgProd.class, this);
    }

    public FrameBuffer getFBO() {
        return fbo;
    }

    @Override
    public Drawable getDrawable() {
        return drawable;
    }

    /** 释放 FBO */
    public void dispose() {
        fbo.dispose();
    }
    @Override
    public void sink(ImgProd product){
        prods.add(product);
    }
    @Override
    public void draw(Batch batch, float parentAlpha){
        batch.end();
        fbo.begin();
        batch.begin();
        Gdx.graphics.getGL20().glClearColor( 0, 0, 0, 1 );
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        for (ImgProd prod : prods) {
            prod.updateAndDraw(batch);
        }
        batch.end();
        fbo.end();
        batch.begin();
        super.draw(batch, parentAlpha);
        prods.clear();
    }
}
