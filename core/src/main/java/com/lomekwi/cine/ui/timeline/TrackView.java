package com.lomekwi.cine.ui.timeline;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;

public class TrackView extends Group {
    @Override
    public void addActor (Actor actor){
        actor.setHeight(getHeight());
        super.addActor(actor);
    }
    @Override
    public void addActorAfter (Actor actorAfter, Actor actor){
        actor.setHeight(getHeight());
        super.addActorAfter(actorAfter, actor);
    }
    @Override
    public void addActorBefore (Actor actorBefore, Actor actor){
        actor.setHeight(getHeight());
        super.addActorBefore(actorBefore, actor);
    }
    @Override
    public void addActorAt (int index, Actor actor){
        actor.setHeight(getHeight());
        super.addActorAt(index, actor);
    }
}
