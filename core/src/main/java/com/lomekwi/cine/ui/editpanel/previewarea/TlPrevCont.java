package com.lomekwi.cine.ui.editpanel.previewarea;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.lomekwi.cine.GlobalVars;
import com.lomekwi.cine.pipeline.Sink;
import com.lomekwi.cine.pipeline.image.ImgProd;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责 FBO 的创建、渲染和资源管理
 */
public class TlPrevCont implements Sink<ImgProd> {
    private final FrameBuffer fbo;
    private final List<ImgProd> prods = new ArrayList<>();
    private final TextureRegionDrawable drawable;
    private final Matrix4 oldMatrix = new Matrix4();

    public TlPrevCont(int width, int height) {
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        TextureRegion region = new TextureRegion(fbo.getColorBufferTexture());
        region.flip(false, true);
        drawable = new TextureRegionDrawable(region);
        GlobalVars.getProject().distributor.registerSink(ImgProd.class, this);
    }

    public FrameBuffer getFbo() {
        return fbo;
    }

    @Override
    public void sink(ImgProd product) {
        prods.add(product);
    }

    /** 渲染到 FBO */
    public void render(Batch batch) {
        batch.end();

        // 保存当前投影矩阵
        oldMatrix.set(batch.getProjectionMatrix());

        fbo.begin();

        // 设置正交投影，使世界坐标直接对应 FBO 像素坐标
        batch.getProjectionMatrix().setToOrtho2D(0, 0, fbo.getWidth(), fbo.getHeight());
        batch.begin();

        // 清除 FBO
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 绘制所有产品到 FBO
        for (ImgProd prod : prods) {
            prod.updateAndDraw(batch);
        }

        batch.end();                 // 结束 FBO 绘制
        fbo.end();                   // 解绑 FBO（恢复默认帧缓冲）

        // 恢复原来的投影矩阵
        batch.getProjectionMatrix().set(oldMatrix);
        batch.begin();

        prods.clear();
    }

    public void dispose() {
        fbo.dispose();
    }

    public TextureRegionDrawable getDrawable() {
        return drawable;
    }
}
