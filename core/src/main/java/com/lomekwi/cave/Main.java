package com.lomekwi.cave;

import com.badlogic.gdx.ApplicationAdapter;
import com.lomekwi.cave.ui.Root;
import com.lomekwi.cave.util.Vars;

import games.spooky.gdx.nativefilechooser.NativeFileChooser;


/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private Root ui;
    private final AppEvents.UpdateEvent evt = new AppEvents.UpdateEvent();
    public Main (NativeFileChooser fileChooser) {
        Vars.fileChooser = fileChooser;
    }
    @Override
    public void create() {
        ui = new Root(this);
        ui.create();
    }

    @Override
    public void render() {
        Vars.appEventBus.post(evt);
        ui.render();
    }

    @Override
    public void dispose() {
        ui.dispose();
    }
    //测试用
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
