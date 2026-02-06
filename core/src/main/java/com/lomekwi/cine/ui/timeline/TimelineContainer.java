package com.lomekwi.cine.ui.timeline;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;

public class TimelineContainer extends Container<Group> {

    private final Rectangle scissors = new Rectangle();
    private final Rectangle clipBounds = new Rectangle();
    private int moveLength=20;


    public TimelineContainer(Group contentGroup) {
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
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                Group group = getActor();
                group.moveBy(amountY*moveLength,0);
                return true;
            }
        });
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
