package com.lomekwi.cine.ui;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.lomekwi.cine.Main;
import com.lomekwi.cine.project.Project;

public class Root implements ApplicationListener {
    private final Stage stage;
    private final Main main;

    public Root(Main main) {
        this.main = main;
        TextureView textureView = new TextureView(getProject().getPlayController().getOutputDispatcher());

        stage = new Stage();
        stage.addActor(textureView);
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

    }

    @Override
    public void dispose() {
    }
    public Project getProject(){
        return main.getProject();
    }
}
