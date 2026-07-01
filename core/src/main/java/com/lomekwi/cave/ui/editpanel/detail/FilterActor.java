package com.lomekwi.cave.ui.editpanel.detail;

import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisWindow;

public abstract class FilterActor extends VisWindow {
    private Runnable onRemove;

    public FilterActor(String title) {
        super(title);
        align(Align.top | Align.left);
        defaults().left();
        addCloseButton();
    }

    public void setOnRemoveListener(Runnable r) {
        onRemove = r;
    }

    @Override
    public void close() {
        if (onRemove != null) {
            onRemove.run();
        }
    }
}
