package com.lomekwi.cine.ui;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.lomekwi.cine.Main;
import com.lomekwi.cine.project.Project;

public class Root implements ApplicationListener {
    private final Stage stage;
    private final Main main;
    private final TestOutput testOutput;

    public Root(Main main) {
        this.main = main;
        testOutput=new TestOutput(this);

        stage = new Stage();
    }

    @Override
    public void create() {

    }

    @Override
    public void render() {
        testOutput.update();
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
