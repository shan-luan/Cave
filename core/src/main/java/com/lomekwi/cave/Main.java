package com.lomekwi.cave;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.lomekwi.cave.project.Project;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.util.Vars;

import games.spooky.gdx.nativefilechooser.NativeFileChooser;


/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private Root ui;
    public Main (NativeFileChooser fileChooser) {
        Vars.fileChooser = fileChooser;
    }
    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);
        ui = new Root(this);
        ui.create();
    }

    @Override
    public void render() {
        Project p = ui.getFrontendProject();
        if (p != null) {
            p.update();
        }
        ui.render();
    }

    @Override
    public void dispose() {
        ui.dispose();
    }
    @Override
    public void resize(int width, int height) {
        ui.resize(width, height);
    }
    @Override
    public void pause() {
        ui.pause();
    }
    @Override
    public void resume() {
        ui.resume();
    }
}
