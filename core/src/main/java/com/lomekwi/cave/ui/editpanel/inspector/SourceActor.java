package com.lomekwi.cave.ui.editpanel.inspector;

import com.badlogic.gdx.utils.Align;
import com.lomekwi.cave.ui.widget.Card;

public abstract class SourceActor extends Card {
    public SourceActor(String title) {
        super(title);
        align(Align.top | Align.left);
        defaults().left();
    }
}
