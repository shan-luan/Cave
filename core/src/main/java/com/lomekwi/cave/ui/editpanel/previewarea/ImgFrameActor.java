package com.lomekwi.cave.ui.editpanel.previewarea;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;
import com.lomekwi.cave.app.App;
import com.lomekwi.cave.pipeline.image.ImgFrame;
import com.lomekwi.cave.timeline.Segment;

public class ImgFrameActor extends Image {
    private final ImgFrame imgFrame;
    private boolean selected;
    private static final Color SELECTED_COLOR = new Color(1, 1, 1, 0.8f);

    public ImgFrameActor(ImgFrame imgFrame) {
        super(imgFrame.getTexture());
        this.imgFrame = imgFrame;
        setScaling(Scaling.stretch);
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Segment segment = imgFrame.getSource() != null ? imgFrame.getSource().getSegment() : null;
                if (segment != null && segment.getTrack() != null) {
                    var editPanel = App.root.getFrontendEditPanel();
                    if (editPanel != null) {
                        var tlGroup = editPanel.getTlGroup();
                        if (tlGroup != null) {
                            boolean addToSelection = com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT);
                            tlGroup.selectSegment(segment, addToSelection);
                        }
                    }
                }
            }
        });
    }

    public ImgFrame getImgFrame() {
        return imgFrame;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        if (selected) {
            App.root.getShapeDrawer().rectangle(
                getX(), getY(), getWidth(), getHeight(),
                SELECTED_COLOR, 2f
            );
        }
    }
}
