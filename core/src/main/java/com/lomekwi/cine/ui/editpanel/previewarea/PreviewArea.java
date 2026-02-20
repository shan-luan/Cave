package com.lomekwi.cine.ui.editpanel.previewarea;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Scaling;

/**
 * 负责显示 FBO 输出
 */
//FIXME：dispose从未被调用
public class PreviewArea extends Image {
    private final TlPrevCont content;

    public PreviewArea(TlPrevCont content) {
        super(content.getDrawable(), Scaling.fill);
        this.content = content;
    }
    @Override
    public void draw(Batch batch, float parentAlpha) {
        content.render(batch);
        super.draw(batch, parentAlpha);
    }
    public void dispose() {
        content.dispose();
    }

}
