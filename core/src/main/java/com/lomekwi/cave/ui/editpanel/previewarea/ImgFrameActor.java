package com.lomekwi.cave.ui.editpanel.previewarea;

import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Scaling;
import com.lomekwi.cave.pipeline.image.ImgFrame;

public class ImgFrameActor extends Image {
    private final ImgFrame imgFrame;

    public ImgFrameActor(ImgFrame imgFrame) {
        super(imgFrame.getTexture());
        this.imgFrame = imgFrame;
        setScaling(Scaling.stretch);
    }

    public ImgFrame getImgFrame() {
        return imgFrame;
    }
}
