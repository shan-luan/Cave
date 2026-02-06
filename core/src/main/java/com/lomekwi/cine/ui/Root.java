package com.lomekwi.cine.ui;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.lomekwi.cine.Main;
import com.lomekwi.cine.project.Project;
import com.lomekwi.cine.ui.timeline.TimelineContainer;

public class Root implements ApplicationListener {
    private final Stage stage;
    private final Main main;
    TextureView textureView;
    public Root(Main main) {
        VisUI.load();

        this.main = main;

        textureView = new TextureView(getProject().getPlayController().getOutputDispatcher());
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        textureView.setSize(1920,1080);
        TimelineContainer timelineContainer=new TimelineContainer(textureView);
        stage.addActor(timelineContainer);
        stage.setScrollFocus(timelineContainer);
    }

    @Override
    public void create() {

    }

    @Override
    public void render() {
        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
        stage.draw();
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        VisUI.dispose();
    }
    public Project getProject(){
        return main.getProject();
    }
}
