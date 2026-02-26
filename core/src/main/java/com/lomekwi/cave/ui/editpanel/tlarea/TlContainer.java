package com.lomekwi.cave.ui.editpanel.tlarea;

import com.badlogic.gdx.scenes.scene2d.ui.Container;

public class TlContainer extends Container<TlActor> {
    public TlContainer(TlActor actor) {
        super(actor);
        setClip(true);
    }
    @Override
    public void validate(){
        super.validate();
        getActor().setBounds(getX(),getY(),getWidth(),getHeight());
    }
}
