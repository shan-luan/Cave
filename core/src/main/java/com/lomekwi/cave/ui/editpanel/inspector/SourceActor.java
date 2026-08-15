package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisWindow;

public abstract class SourceActor extends VisWindow {
    public SourceActor(String title) {
        super(title);
        align(Align.top | Align.left);
        defaults().left();
        setKeepWithinStage(false);
        setMovable(false);
    }
}
