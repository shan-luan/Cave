package com.lomekwi.cine.ui.editpanel.previewarea;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public abstract class PreviewContent {
    private final FrameBuffer fbo;
    private final TextureRegionDrawable drawable;

    public PreviewContent(int width, int height){
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        TextureRegion region = new TextureRegion(fbo.getColorBufferTexture());
        region.flip(false, true);
        drawable = new TextureRegionDrawable(region);
    }

    public FrameBuffer getFBO(){ return fbo; }
    public Drawable getDrawable(){ return drawable; }

    public void dispose(){ fbo.dispose(); }
}
