package com.lomekwi.cave.ui.topbar;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.lomekwi.cave.ui.Root;

public class About extends ChangeListener {
    @Override
    public void changed(ChangeEvent event, Actor actor) {
        Dialogs.showOKDialog(Root.getInstance().getStage(), "About", "CAVE. Short for Cave Is Non-linear Editor. It's open source and free. ");
    }
}
