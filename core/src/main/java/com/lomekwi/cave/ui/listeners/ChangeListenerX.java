package com.lomekwi.cave.ui.listeners;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

public class ChangeListenerX extends ChangeListener {
    private final Runnable runnable;
    public ChangeListenerX(Runnable runnable){
        this.runnable=runnable;
    }
    @Override
    public void changed(ChangeEvent event, Actor actor) {
        runnable.run();
    }
}
