package com.lomekwi.cine.ui.widget;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.math.MathUtils;

public class CameraView extends Container<Group> {

    private final Rectangle scissors = new Rectangle();
    private final Rectangle clipBounds = new Rectangle();
    private final Vector2 lastTouch = new Vector2();
    private final Vector2 tmpVec = new Vector2();

    private float minScale = 0f;
    private float maxScale = 30000000000000000f;

    public CameraView(Group contentGroup) {
        super(contentGroup);
        setFillParent(false);
        contentGroup.setOrigin(0, 0);
        contentGroup.setPosition(0, 0);
        contentGroup.setScale(1f);
        addInputListener();
        setSize(contentGroup.getWidth(), contentGroup.getHeight());
    }

    private void addInputListener() {
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                lastTouch.set(x, y);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                Group group = getContentGroup();
                group.moveBy(x - lastTouch.x, y - lastTouch.y);
                lastTouch.set(x, y);
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                return handleScrolled(event, amountY);
            }
        });
    }

    private boolean handleScrolled(InputEvent event, float amountY) {
        Group group = getContentGroup();

        float oldScale = group.getScaleX();
        float newScale = MathUtils.clamp(oldScale * (1 - amountY * 0.1f), minScale, maxScale);
        float scaleRatio = newScale / oldScale;

        tmpVec.set(event.getStageX(), event.getStageY());
        stageToLocalCoordinates(tmpVec);

        float dx = tmpVec.x - group.getX();
        float dy = tmpVec.y - group.getY();

        group.setScale(newScale);
        group.setPosition(
            tmpVec.x - dx * scaleRatio,
            tmpVec.y - dy * scaleRatio
        );

        return true;
    }

    public Group getContentGroup() {
        return getActor();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!isVisible()) return;
        batch.flush();

        clipBounds.set(getX(), getY(), getWidth(), getHeight());
        ScissorStack.calculateScissors(
            getStage().getCamera(),
            batch.getTransformMatrix(),
            clipBounds,
            scissors
        );

        if (ScissorStack.pushScissors(scissors)) {
            super.draw(batch, parentAlpha);
            batch.flush();
            ScissorStack.popScissors();
        }
    }
}
