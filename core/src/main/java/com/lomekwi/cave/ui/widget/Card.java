package com.lomekwi.cave.ui.widget;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisTable;

public class Card extends VisTable {
    private final Label titleLabel;
    private final Table titleTable;
    private boolean drawTitleTable;

    public Card(String title) {
        super();
        Window.WindowStyle style = VisUI.getSkin().get(Window.WindowStyle.class);
        setBackground(style.background);
        setClip(true);

        titleLabel = new Label(title, new Label.LabelStyle(style.titleFont, style.titleFontColor));
        titleLabel.setEllipsis(true);
        titleLabel.setAlignment(VisUI.getDefaultTitleAlign());

        titleTable = new Table() {
            @Override
            public void draw(Batch batch, float parentAlpha) {
                if (drawTitleTable) super.draw(batch, parentAlpha);
            }
        };
        titleTable.add(titleLabel).growX().minWidth(0);
        addActor(titleTable);
    }

    @Override
    protected void drawBackground(Batch batch, float parentAlpha, float x, float y) {
        super.drawBackground(batch, parentAlpha, x, y);
        titleTable.getColor().a = getColor().a;
        float padTop = getPadTop(), padLeft = getPadLeft();
        titleTable.setSize(getWidth() - padLeft - getPadRight(), padTop);
        titleTable.setPosition(padLeft, getHeight() - padTop);
        drawTitleTable = true;
        titleTable.draw(batch, parentAlpha);
        drawTitleTable = false;
    }

    @Override
    public float getPrefWidth() {
        return Math.max(super.getPrefWidth(), titleTable.getPrefWidth() + getPadLeft() + getPadRight());
    }

    public Label getTitleLabel() {
        return titleLabel;
    }

    public Table getTitleTable() {
        return titleTable;
    }

    public void addCloseButton() {
        VisImageButton closeButton = new VisImageButton("close-window");
        titleTable.add(closeButton).padRight(-getPadRight() + 0.7f);
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                close();
            }
        });
        closeButton.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                event.cancel();
                return true;
            }
        });
        if (titleLabel.getLabelAlign() == Align.center && titleTable.getChildren().size == 2) {
            titleTable.getCell(titleLabel).padLeft(closeButton.getWidth() * 2);
        }
    }

    protected void close() {
        remove();
    }
}
